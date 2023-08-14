package com.flutter.akka.actors.classic

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor

object Account {
  sealed trait AccountCommand

  sealed trait AccountEvent

  def props(accountNo:String):Props = {
    Props(new Account(accountNo))
  }
}

class Account (accountNo: String) extends PersistentActor with ActorLogging {
  override def receiveRecover: Receive = ???

  override def receiveCommand: Receive = ???

  override def persistenceId: String = ???
}
