package com.flutter.akka.streams

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.Timeout
import com.flutter.akka.actors.classic.Account
import com.flutter.akka.actors.classic.Account.{AccountCommand, AccountEvent, Deposit, GetBalance, Withdraw}
import com.flutter.akka.proto.Messages.AccountMessage
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.concurrent.duration._

object AccountStream extends App {

  case class InMessage(command:AccountCommand, committableMessage: Option[CommittableMessage[String, Array[Byte]]] = None)

  implicit val system = ActorSystem.create("AccountStream")
  implicit val ec = system.dispatcher
  implicit val askTimeout: Timeout = 5.seconds
  val accountRegion: ActorRef = ClusterSharding (system).start(typeName = "Account",
                                                              entityProps = Account.props(),
                                                              settings = ClusterShardingSettings(system),
                                                              extractEntityId = Account.extractEntityId,
                                                              extractShardId = Account.extractShardId)

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
      .map(m => parseConsumerRecord(m))
      .map { m => println(m)
        m
      }
      .ask[AccountEvent](parallelism = 5)(accountRegion)
      .map(println)
      .toMat(Sink.ignore)(Keep.both)
  }

  stream.run()
}
