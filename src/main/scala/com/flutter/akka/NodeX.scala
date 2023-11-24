package com.flutter.akka

import akka.actor.ActorSystem
import akka.kafka.{ManualSubscription, Subscriptions}
import com.flutter.akka.streams.AccountStream
import org.apache.kafka.common.TopicPartition

object NodeX extends App with Node {
  private implicit val system: ActorSystem = akkaSystem("SeparateCluster", 2554, List("akka://SeparateCluster@127.0.0.1:2554"))

  val topicPartition = new TopicPartition(kafkaTopic, 0)
  val subscription: ManualSubscription = Subscriptions.assignment(topicPartition)
  AccountStream.subscriptionStreamWithCommit("ManualSubscriptionConsumer", subscription).run()

}
