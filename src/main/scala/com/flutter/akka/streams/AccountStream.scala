package com.flutter.akka.streams

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.pattern.ask
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Zip}
import akka.util.Timeout
import com.flutter.akka.actors.classic.Account
import com.flutter.akka.actors.classic.Account.{AccountCommand, AccountEvent, Deposit, GetBalance, Withdraw}
import com.flutter.akka.proto.Messages.AccountMessage
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.concurrent.duration._

object AccountStream extends App {

  case class InMessage(command:AccountCommand)

  private implicit val system: ActorSystem = ActorSystem.create("AccountStream")

  private val accountRegion: ActorRef = ClusterSharding (system).start(typeName = "Account",
                                                              entityProps = Account.props(),
                                                              settings = ClusterShardingSettings(system),
                                                              extractEntityId = Account.extractEntityId,
                                                              extractShardId = Account.extractShardId)

  private val kafkaServers = "kafka:9092"
  private val consumerConfig = system.settings.config.getConfig("akka.kafka.consumer")

  private val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(kafkaServers)
    .withGroupId("akkaSandbox")
    .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
    .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    //.withProperty(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.RoundRobinAssignor")


  private def parseAccountMessage : AccountMessage => AccountCommand = {
    am =>
      am.getPayload.getPayloadCase.getNumber match {
        case 1 => Deposit(am.getAccountNo, am.getPayload.getDeposit.getAmount)
        case 2 => Withdraw(am.getAccountNo, am.getPayload.getWithdraw.getAmount)
        case 3 => GetBalance(am.getAccountNo)
      }
  }

  private def parseConsumerRecord : ConsumerRecord[String, Array[Byte]] => InMessage = {
    cr =>
      val accountMessage = com.flutter.akka.proto.Messages.AccountMessage.parseFrom(cr.value())
      InMessage(parseAccountMessage(accountMessage))
  }

  def parseCommittableMessage : CommittableMessage[String, Array[Byte]] => InMessage = {
    cm =>
      val accountMessage = com.flutter.akka.proto.Messages.AccountMessage.parseFrom(cm.record.value())
      InMessage(parseAccountMessage(accountMessage))
  }

  private def stream2 = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      implicit val askTimeout: Timeout = 5.seconds

      val src = Consumer.committableSource(consumerSettings, Subscriptions.topics("AccountTopic"))
      val broadcast = builder.add(Broadcast[CommittableMessage[String, Array[Byte]]](2))
      val zip = builder.add(Zip[Any, CommittableMessage[String, Array[Byte]]])

      val businessLogic = Flow[CommittableMessage[String, Array[Byte]]]
        .mapAsync(1)(message => ask(accountRegion, parseCommittableMessage(message))).wireTap(ev => println(ev))

      val snk = Flow[CommittableMessage[String, Array[Byte]]].mapAsync(1)(message => message.committableOffset.commitScaladsl()).to(Sink.ignore)

      src ~> broadcast
             broadcast ~> businessLogic ~> zip.in0
             broadcast ~> zip.in1
                          zip.out.map(_._2) ~> snk
      ClosedShape
  })

  private def stream = {

    implicit val askTimeout: Timeout = 5.seconds

    Consumer.plainPartitionedSource(consumerSettings, Subscriptions.topics("AccountTopic"))
      .flatMapMerge(5, _._2)
      .map(m => parseConsumerRecord(m))
      .wireTap(m => println(m))
      .ask[AccountEvent](parallelism = 5)(accountRegion)
      .wireTap(m => println(m))
      .toMat(Sink.ignore)(Keep.both)
  }

  stream2.run()
}
