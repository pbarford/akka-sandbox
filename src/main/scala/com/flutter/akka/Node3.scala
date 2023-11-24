package com.flutter.akka

import akka.actor.{ActorSystem, Props}
import akka.kafka.{AutoSubscription, Subscriptions}
import com.flutter.akka.kafka.RebalanceListener
import com.flutter.akka.streams.AccountStream

object Node3 extends App with Node {

  private implicit val system: ActorSystem = akkaSystem("AccountStream", 2553, accountSystemSeedNodes)

  val rebalanceListener = system.actorOf(Props(new RebalanceListener))
  val subscription: AutoSubscription = Subscriptions.topics("PartitionedTopic").withRebalanceListener(rebalanceListener)
  AccountStream.subscriptionStreamWithCommit("AccountStreamConsumerGroup", subscription).run()

}
