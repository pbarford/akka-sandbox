package com.flutter.akka

import akka.actor.ActorSystem
import akka.kafka.{ManualSubscription, Subscriptions}
import com.flutter.akka.streams.AccountStream
import org.apache.kafka.common.TopicPartition

object NodeY extends App with Node {
  private implicit val system: ActorSystem = akkaSystem("SeparateCluster", 2555, List("akka://SeparateCluster@127.0.0.1:2555"))

  val topicPartition = new TopicPartition(kafkaTopic, 0)
  val subscription: ManualSubscription = Subscriptions.assignment(topicPartition)
  AccountStream.subscriptionStreamWithCommit("ManualSubscriptionConsumer", subscription).run()
}
