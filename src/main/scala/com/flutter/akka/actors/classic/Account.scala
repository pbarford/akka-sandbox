package com.flutter.akka.actors.classic

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.typed.state.RecoveryCompleted
import akka.persistence._
import com.flutter.akka.actors.classic.Account._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
object Account {

  sealed trait AccountCommand {
    def accountNo:String
  }

  case object Die
  case object Stop
  case class Deposit(accountNo: String, amount: Double) extends AccountCommand
  case class Withdraw(accountNo: String, amount: Double) extends AccountCommand
  case class GetBalance(accountNo: String) extends AccountCommand

  sealed trait AccountEvent {
    def accountNo:String
    def timestamp:Long
  }

  case class AccountCredited(accountNo: String, timestamp: Long, amount: Double, balance: Double) extends AccountEvent
  case class AccountDebited(accountNo: String, timestamp: Long, amount: Double) extends AccountEvent
  case class WithdrawalDeclined(accountNo: String, timestamp: Long, amount: Double) extends AccountEvent
  case class AccountBalance(accountNo: String, timestamp: Long, totalBalance: Double, transactions:List[AccountEvent]) extends AccountEvent

  case class AccountState(accountNo: String, seqNo:Long = 0, balance: Double = 0.0, transactions: List[AccountEvent] = List.empty) {
    def apply: AccountEvent => AccountState = {
      case credit: AccountCredited => copy(seqNo = seqNo + 1, balance = balance + credit.amount, transactions = credit :: transactions)
      case debit: AccountDebited => copy(seqNo = seqNo + 1, balance= balance - debit.amount, transactions = debit :: transactions)
      case declined: WithdrawalDeclined => copy(seqNo = seqNo + 1, transactions = declined :: transactions)
      case _ => this
    }
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg@Deposit(id, _) => (id, msg)
    case msg@Withdraw(id, _) => (id, msg)
    case msg@GetBalance(id) => (id, msg)
  }

  /**
    * "https://doc.akka.io/docs/akka/current/cluster-sharding.html"
    *
    * As a rule of thumb, the number of shards should be a factor ten greater than the planned maximum number of cluster nodes
    *
    * IMPORTANT : The sharding algorithm must be the same on all nodes in a running cluster. It can be changed after stopping all nodes in the cluster.
    */

  val numberOfShards = 30

  val extractShardId: ShardRegion.ExtractShardId = {
    case Deposit(id, _) => (id.hashCode % numberOfShards).toString
    case Withdraw(id, _) => (id.hashCode % numberOfShards).toString
    case GetBalance(id) => (id.hashCode % numberOfShards).toString
    case ShardRegion.StartEntity(id) => (id.hashCode % numberOfShards).toString
    case _ => throw new IllegalArgumentException()
  }

  def props(): Props = {
    Props(new Account())
  }
}

class Account() extends PersistentActor with ActorLogging {

  private implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher

  context.system.scheduler.scheduleOnce(5.seconds, self, Die)

  private def accountNo: String = self.path.name

  private var state: AccountState = AccountState(accountNo)

  private def applyCommand: AccountCommand => AccountEvent = {
    case Deposit(_, amount) => AccountCredited(accountNo = accountNo, timestamp = System.currentTimeMillis(), amount = amount, balance = state.balance + amount)
    case GetBalance(_) => AccountBalance(accountNo, System.currentTimeMillis(), state.balance, state.transactions)
    case Withdraw(_, amount) if amount > state.balance => WithdrawalDeclined(accountNo, System.currentTimeMillis(), amount)
    case Withdraw(_, amount) if amount <= state.balance => AccountDebited(accountNo, System.currentTimeMillis(), amount)
  }

  private def applyEventToState: AccountEvent => AccountEvent = { ev =>
    state = state.apply(ev)
    if(!recoveryRunning && state.seqNo % 3 == 0) saveSnapshot(state)
    ev
  }

  override def receiveRecover: Receive = {
    case SnapshotOffer(metadata, snapshot:AccountState) =>
      log.info(s"Snapshot received HWM [${metadata.sequenceNr}]")
      state = snapshot
    case ev: AccountEvent => applyEventToState(ev)
    case _: RecoveryCompleted => ()
  }

  private def replyToSender(ref: ActorRef): AccountEvent => Unit = { ev =>
    ref ! ev
  }

  private def persistAndReply(ref: ActorRef)(ev: AccountEvent): Unit = {
    persist(ev)(applyEventToState.andThen(replyToSender(ref)))
  }

  override def receiveCommand: Receive = {
    case cmd: AccountCommand =>
      log.info(s"Actor::Account --> AccountCommand received [$cmd]")
      applyCommand.andThen(persistAndReply(sender()))(cmd)

    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Snapshot saved seqNo [${metadata.sequenceNr}]")
      deleteMessages(metadata.sequenceNr)
      deleteSnapshots(SnapshotSelectionCriteria.create(metadata.sequenceNr - 1, System.currentTimeMillis()))

    case DeleteSnapshotsSuccess(criteria) => log.info(s"DeleteSnapshotsSuccess to seqNo [${criteria.maxSequenceNr}]")
    case DeleteMessagesSuccess(toSeqNo) => log.info(s"DeleteMessagesSuccess to seqNo [$toSeqNo]")

    case Die =>
      log.info(s"Die received, committing hare kari")
      context.parent ! Passivate(stopMessage = Stop)

    case Stop =>
      log.info(s"Stop received")
      context.stop(self)
  }
  
  override def persistenceId: String = s"classic-acc-$accountNo"
}
