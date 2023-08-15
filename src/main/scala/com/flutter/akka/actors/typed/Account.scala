package com.flutter.akka.actors.typed

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.scaladsl.EventSourcedBehavior.CommandHandler

object Account {

  sealed trait AccountCommand
  case class StopCommand(accountNo: String) extends AccountCommand

  sealed trait AccountEvent

  case class AccountState(accountNo:String, balance:Double, transactions:List[AccountEvent]) {
    def apply: (ActorContext[AccountCommand], AccountEvent) => AccountState = {
      ???
    }
  }

  private def commandHandler(context: ActorContext[AccountCommand]): CommandHandler[AccountCommand, AccountEvent, AccountState] = { (state, cmd) =>
    cmd match {
      case StopCommand(no) =>
        context.log.info(s"StopCommand:: account=$no")
        Effect.stop()
    }
  }


  def behavior(accountNo: String): Behavior[AccountCommand] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[AccountCommand, AccountEvent, AccountState](
        persistenceId = PersistenceId.ofUniqueId(s"typed-account-$accountNo"),
        emptyState = AccountState(accountNo, 0.0, List.empty),
        commandHandler(context),
        eventHandler = (state, event) => {
          state.apply(context, event)
        }
      ).receiveSignal {
        case (state, RecoveryCompleted) =>
          context.log.info(
            s"RecoveryCompleted :: accountNo[${state.accountNo}] state=$state"
          )
      }
    }
  }

}
