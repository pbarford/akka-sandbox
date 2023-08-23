package com.flutter.akka.streams

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.{Keep, Sink}
import com.flutter.akka.actors.classic.Account.{AccountCommand, Deposit, GetBalance, Withdraw}
import com.flutter.akka.proto.Messages.AccountMessage
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

object AccountStream extends App {

  case class InMessage(command:AccountCommand, committableMessage: Option[CommittableMessage[String, Array[Byte]]] = None)

  implicit val system = ActorSystem.create("AccountStream")
  implicit val ec = system.dispatcher
  val kafkaServers = "kafka:9092"
  val consumerConfig = system.settings.config.getConfig("akka.kafka.consumer")

  val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(kafkaServers)
    .withGroupId("akkaSandbox")
    .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
    .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    //.withProperty(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.RoundRobinAssignor")


  def parseAccountMessage : AccountMessage => AccountCommand = {
    am =>
      am.getPayload.getPayloadCase.getNumber match {
        case 1 => Deposit(am.getAccountNo, am.getPayload.getDeposit.getAmount)
        case 2 => Withdraw(am.getAccountNo, am.getPayload.getWithdraw.getAmount)
        case 3 => GetBalance(am.getAccountNo)
      }
  }

  def parseConsumerRecord : ConsumerRecord[String, Array[Byte]] => InMessage = {
    cr =>
      val accountMessage = com.flutter.akka.proto.Messages.AccountMessage.parseFrom(cr.value())
      InMessage(parseAccountMessage(accountMessage))
  }

  def parseCommittableMessage : CommittableMessage[String, Array[Byte]] => InMessage = {
    cm =>
      val accountMessage = com.flutter.akka.proto.Messages.AccountMessage.parseFrom(cm.record.value())
      InMessage(parseAccountMessage(accountMessage), Some(cm))
  }

  def stream = {

    Consumer.plainPartitionedSource(consumerSettings, Subscriptions.topics("AccountTopic"))
      .flatMapMerge(5, _._2)
      .map(m => parseConsumerRecord(m)).map(println(_))
      .toMat(Sink.ignore)(Keep.both)
  }

  stream.run()
}
