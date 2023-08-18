package com.flutter.akka.actors.classic

import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.persistence.PersistentActor
import akka.persistence.typed.state.RecoveryCompleted
import com.flutter.akka.actors.classic.Account.{AccountBalance, AccountCommand, AccountCredited, AccountDebited, AccountEvent, AccountState, Deposit, GetBalance, Withdraw, WithdrawalDeclined}

object Account {
  
  sealed trait AccountCommand
  case class Deposit(accountNo: String, amount: Double) extends AccountCommand
  case class Withdraw(accountNo: String, amount: Double) extends AccountCommand
  case class GetBalance(accountNo: String) extends AccountCommand

  sealed trait AccountEvent
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

  def props(accountNo: String): Props = {
    Props(new Account(accountNo))
  }
}

class Account(accountNo: String) extends PersistentActor with ActorLogging {

  private var state = AccountState(accountNo)

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
