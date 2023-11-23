package com.flutter.akka.kafka

import akka.actor.{Actor, ActorLogging}
import akka.kafka.{TopicPartitionsAssigned, TopicPartitionsRevoked}

class RebalanceListener extends Actor with ActorLogging {
  override def receive: Receive = {
    case TopicPartitionsAssigned(subscription, topicPartitions) =>
      log.info("RebalanceListener::Assigned: {}", topicPartitions)

    case TopicPartitionsRevoked(subscription, topicPartitions) =>
      log.info("RebalanceListener::Revoked: {}", topicPartitions)
  }
}
