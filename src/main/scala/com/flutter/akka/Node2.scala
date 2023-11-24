package com.flutter.akka

import akka.actor.{ActorSystem, Props}
import akka.kafka.{AutoSubscription, Subscriptions}
import com.flutter.akka.kafka.RebalanceListener
import com.flutter.akka.streams.AccountStream

object Node2 extends App with Node {

  private implicit val system: ActorSystem = akkaSystem("AccountStream", 2552, accountSystemSeedNodes)

  val rebalanceListener = system.actorOf(Props(new RebalanceListener))
  val subscription: AutoSubscription = Subscriptions.topics(kafkaTopic).withRebalanceListener(rebalanceListener)
  AccountStream.subscriptionStreamWithCommit(accountConsumerGroupId, subscription).run()

}
