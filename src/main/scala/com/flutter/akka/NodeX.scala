package com.flutter.akka

import akka.actor.{ActorSystem, Props}
import akka.kafka.{AutoSubscription, Subscriptions}
import com.flutter.akka.Node3.akkaSystem
import com.flutter.akka.kafka.RebalanceListener
import com.flutter.akka.streams.AccountStream

object NodeX extends App {
  private implicit val system: ActorSystem = akkaSystem("SeparateCluster", 2554, List("akka://SeparateCluster@127.0.0.1:2554"))

  val rebalanceListener = system.actorOf(Props(new RebalanceListener))
  val subscription: AutoSubscription = Subscriptions.topics("PartitionedTopic").withRebalanceListener(rebalanceListener)
  AccountStream.partitionedStreamWithCommit(subscription).run()

}
