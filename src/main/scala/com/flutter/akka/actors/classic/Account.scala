package com.flutter.akka.actors.classic

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.PersistentActor
import akka.persistence.typed.state.RecoveryCompleted
import com.flutter.akka.actors.classic.Account.{AccountBalance, AccountCommand, AccountCredited, AccountDebited, AccountEvent, AccountState, Deposit, GetBalance, Withdraw, WithdrawalDeclined}
import com.flutter.akka.streams.AccountStream.InMessage

object Account {

  sealed trait AccountCommand {
    def accountNo:String
  }

  case class Deposit(accountNo: String, amount: Double) extends AccountCommand
  case class Withdraw(accountNo: String, amount: Double) extends AccountCommand
  case class GetBalance(accountNo: String) extends AccountCommand

  sealed trait AccountEvent {
    def accountNo:String
    def timestamp:Long
  }

  case class AccountCredited(accountNo: String, timestamp: Long, amount: Double) extends AccountEvent
  case class AccountDebited(accountNo: String, timestamp: Long, amount: Double) extends AccountEvent
  case class WithdrawalDeclined(accountNo: String, timestamp: Long, amount: Double) extends AccountEvent
  case class AccountBalance(accountNo: String, timestamp: Long, totalBalance: Double, transactions:List[AccountEvent]) extends AccountEvent

  case class AccountState(accountNo: String, balance: Double = 0.0, transactions: List[AccountEvent] = List.empty) {
    def apply: AccountEvent => AccountState = {
      case credit: AccountCredited => copy(balance = balance + credit.amount, transactions = credit :: transactions)
      case debit: AccountDebited => copy(balance= balance - debit.amount, transactions = debit :: transactions)
      case declined: WithdrawalDeclined => copy(transactions = declined :: transactions)
      case _ => this
    }
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case InMessage(command:AccountCommand) => (command.accountNo, command)
    case msg@Deposit(id, _) => (id, msg)
    case msg@Withdraw(id, _) => (id, msg)
    case msg@GetBalance(id) => (id, msg)
  }
  val numberOfShards = 100

  val extractShardId: ShardRegion.ExtractShardId = {
    case InMessage(command:AccountCommand) => (command.accountNo.hashCode % numberOfShards).toString
    case Deposit(id, _) => (id.hashCode % numberOfShards).toString
    case Withdraw(id, _) => (id.hashCode % numberOfShards).toString
    case GetBalance(id) => (id.hashCode % numberOfShards).toString
    case ShardRegion.StartEntity(id) => (id.toLong % numberOfShards).toString
    case _ => throw new IllegalArgumentException()
  }

  def props(): Props = {
    Props(new Account())
  }
}

class Account() extends PersistentActor with ActorLogging {

  private def accountNo: String = self.path.name

  private var state: AccountState = AccountState(accountNo)

  private def applyCommand: AccountCommand => AccountEvent = {
    case Deposit(_, amount) => AccountCredited(accountNo = accountNo, timestamp = System.currentTimeMillis(), amount = amount)
    case GetBalance(_) => AccountBalance(accountNo, System.currentTimeMillis(), state.balance, state.transactions)
    case Withdraw(_, amount) if amount > state.balance => WithdrawalDeclined(accountNo, System.currentTimeMillis(), amount)
    case Withdraw(_, amount) if amount <= state.balance => AccountDebited(accountNo, System.currentTimeMillis(), amount)
  }


  private def applyEventToState: AccountEvent => AccountEvent = { ev =>
    state = state.apply(ev)
    ev
  }

  override def receiveRecover: Receive = {
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
    case cmd: AccountCommand => applyCommand.andThen(persistAndReply(sender()))(cmd)
  }
  
  override def persistenceId: String = s"classic-acc-$accountNo"
}
