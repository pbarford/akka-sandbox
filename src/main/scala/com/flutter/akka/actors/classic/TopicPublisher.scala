package com.flutter.akka.actors.classic

import akka.actor.{Actor, Props}
import akka.cluster.pubsub.DistributedPubSub
import com.flutter.akka.actors.classic.TopicPublisher.Alert

object TopicPublisher {
  case class Alert(message:String)
  def props = Props(new TopicPublisher())
}

class TopicPublisher extends Actor {
  import akka.cluster.pubsub.DistributedPubSubMediator.Publish
  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator

  def receive: Receive = {
    case in: String =>
      val out = in.toUpperCase
      mediator ! Publish("alerts", Alert(out))
  }
}
