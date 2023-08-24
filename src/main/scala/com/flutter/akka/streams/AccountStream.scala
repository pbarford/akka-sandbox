package com.flutter.akka.streams

import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{CommitterSettings, ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.pattern.ask
import akka.stream.{ClosedShape, FlowShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Zip}
import akka.util.Timeout
import com.flutter.akka.actors.classic.Account
import com.flutter.akka.actors.classic.Account.{AccountCommand, AccountEvent, Deposit, GetBalance, Withdraw}
import com.flutter.akka.proto.Messages.AccountMessage
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.concurrent.Future
import scala.concurrent.duration._

object AccountStream extends App {

  private val kafkaTopic = "PartitionedTopic"

  private implicit val system: ActorSystem = ActorSystem.create("AccountStream")

  private val accountRegion: ActorRef = ClusterSharding (system).start(typeName = "Account",
                                                              entityProps = Account.props(),
                                                              settings = ClusterShardingSettings(system),
                                                              extractEntityId = Account.extractEntityId,
                                                              extractShardId = Account.extractShardId)

  private val kafkaServers = "kafka:9092"
  private val consumerConfig = system.settings.config.getConfig("akka.kafka.consumer")
  val committerSettings = CommitterSettings(system)

  private def parseAccountMessage : AccountMessage => AccountCommand = {
    am =>
      am.getPayload.getPayloadCase.getNumber match {
        case 1 => Deposit(am.getAccountNo, am.getPayload.getDeposit.getAmount)
        case 2 => Withdraw(am.getAccountNo, am.getPayload.getWithdraw.getAmount)
        case 3 => GetBalance(am.getAccountNo)
      }
  }

  private def parseBytes : Array[Byte] => AccountMessage = { bytes =>
    com.flutter.akka.proto.Messages.AccountMessage.parseFrom(bytes)
  }

  private def parseKafkaRecord : Array[Byte] => AccountCommand = {
    bytes =>
      parseAccountMessage(parseBytes(bytes))
  }

  private def mainLogic(): Flow[CommittableMessage[String, Array[Byte]], (Any, CommittableMessage[String, Array[Byte]]), NotUsed] = Flow.fromGraph(GraphDSL.create() {
    implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      implicit val askTimeout: Timeout = 5.seconds

      val broadcast = builder.add(Broadcast[CommittableMessage[String, Array[Byte]]](2))
      val zip = builder.add(Zip[Any, CommittableMessage[String, Array[Byte]]])
      val businessLogic: Flow[CommittableMessage[String, Array[Byte]], Any, NotUsed] = Flow[CommittableMessage[String, Array[Byte]]]
        .mapAsync(1)(message => ask(accountRegion, parseKafkaRecord(message.record.value())))
        .wireTap(ev => println(ev))

      broadcast ~> businessLogic ~> zip.in0
      broadcast ~> zip.in1

      FlowShape(broadcast.in, zip.out)
  })

  private def partitionedStreamWithCommit = {

      val partitions = 5
      val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(kafkaServers)
        .withGroupId("akkaSandboxCommit")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
        .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .withProperty(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.RoundRobinAssignor")

      val src = Consumer.committablePartitionedSource(consumerSettings, Subscriptions.topics(kafkaTopic))

      src.mapAsyncUnordered(partitions) {
        case (partition, source) =>
            println(s"Source for [${partition.topic()}] partition [${partition.partition()}]")
            source.via(mainLogic()).map(_._2.committableOffset).runWith(Committer.sink(committerSettings))
      }.toMat(Sink.ignore)(DrainingControl.apply)
  }

  private def streamWithCommit: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(kafkaServers)
        .withGroupId("akkaSandboxCommit")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
        .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .withProperty(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.RoundRobinAssignor")

      val src = Consumer.committableSource(consumerSettings, Subscriptions.topics(kafkaTopic))
      val sink: Sink[ConsumerMessage.Committable, NotUsed] = Committer.flow(committerSettings).to(Sink.ignore)

        src ~> mainLogic().map(_._2.committableOffset) ~> sink
      ClosedShape
  })

  private def streamNoCommit: RunnableGraph[(Consumer.Control, Future[Done])] = {

    implicit val askTimeout: Timeout = 5.seconds

    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(kafkaServers)
      .withGroupId("akkaSandboxNoCommit")
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
      .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
      .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.RoundRobinAssignor")

    Consumer.plainPartitionedSource(consumerSettings, Subscriptions.topics(kafkaTopic))
      .flatMapMerge(5, _._2)
      .map(m => parseKafkaRecord(m.value()))
      .wireTap(m => println(m))
      .ask[AccountEvent](parallelism = 5)(accountRegion)
      .wireTap(m => println(m))
      .toMat(Sink.ignore)(Keep.both)
  }

  partitionedStreamWithCommit.run()
}
