package com.flutter.akka.actors.classic

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import com.flutter.akka.actors.classic.AlertPublisher.Alert

object AlertPublisher {
  case class Alert(message:String)
  def props = Props(new AlertPublisher())
}

class AlertPublisher extends Actor {
  import akka.cluster.pubsub.DistributedPubSubMediator.Publish
  private val mediator: ActorRef = DistributedPubSub(context.system).mediator

  def receive: Receive = {
    case in: String =>
      val out = in.toUpperCase
      mediator ! Publish("alerts", Alert(out))
  }
}
