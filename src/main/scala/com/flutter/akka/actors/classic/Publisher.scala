package com.flutter.akka.actors.classic

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import com.flutter.akka.actors.classic.Account.AccountEvent
import com.flutter.akka.actors.classic.Publisher.{Publish, Published}

object Publisher {
  case class Publish(event:AccountEvent)
  case class Published(event:AccountEvent)
  def props:Props = Props(new Publisher)
}

class Publisher extends Actor with ActorLogging {

  override def receive: Receive = {
    case publish:Publish =>
      log.info(s"Actor::Publisher --> publishing [${publish.event}]")
      sender() ! Published(publish.event)
    case PoisonPill => log.info(s"Publisher stopping")
  }
}
