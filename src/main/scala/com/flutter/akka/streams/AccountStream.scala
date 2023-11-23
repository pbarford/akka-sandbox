package com.flutter.akka.streams

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.LoggingAdapter
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{AutoSubscription, CommitterSettings, ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Zip}
import akka.stream.{ClosedShape, FlowShape}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.flutter.akka.actors.classic.Account
import com.flutter.akka.actors.classic.Account._
import com.flutter.akka.actors.classic.Publisher.{Publish, Published}
import com.flutter.akka.proto.Messages.AccountMessage
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.concurrent.Future
import scala.concurrent.duration._

object AccountStream {

  private val kafkaTopic = "PartitionedTopic"
  //private val kafkaTopic = "AccountTopic"
  private val kafkaServers = "kafka:9092"

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

  private def translateFlow: Flow[CommittableMessage[String, Array[Byte]], AccountCommand, NotUsed] = {
    Flow[CommittableMessage[String, Array[Byte]]]
      .map(message => parseKafkaRecord(message.record.value()))
  }

  private def processFlow(log:LoggingAdapter, accountRegion:ActorRef): Flow[AccountCommand, AccountEvent, NotUsed] = {
    implicit val askTimeout: Timeout = 5.seconds
    Flow[AccountCommand]
      .wireTap(m => log.info(s"message process --> command [$m]"))
      .ask[AccountEvent](1)(accountRegion)
      .wireTap(ev => log.info(s"message processed --> event [$ev]"))
  }

  private def publishFlow(log:LoggingAdapter, publisher:ActorRef): Flow[AccountEvent, Published, NotUsed] = {
    implicit val askTimeout: Timeout = 5.seconds
      Flow[AccountEvent]
        .map(ev => Publish(ev))
        .wireTap(publish => log.info(s"publish --> [$publish]"))
        .ask[Published](1)(publisher)
        .wireTap(published => log.info(s"published --> [$published]"))
  }

  private def monitorFlow(log:LoggingAdapter): Flow[CommittableMessage[String, Array[Byte]], CommittableMessage[String, Array[Byte]], NotUsed] =
    Flow[CommittableMessage[String, Array[Byte]]].wireTap(m => log.info(s"incoming message :: partition [${m.record.partition()}] offset [${m.committableOffset.partitionOffset.offset}]"))

  private def mainLogic(implicit system: ActorSystem, accountRegion:ActorRef, publisher:ActorRef): Flow[CommittableMessage[String, Array[Byte]], (Any, CommittableMessage[String, Array[Byte]]), NotUsed] = Flow.fromGraph(GraphDSL.create() {
    implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[CommittableMessage[String, Array[Byte]]](2))
      val zip = builder.add(Zip[Any, CommittableMessage[String, Array[Byte]]])

      val businessLogic =
        Flow[CommittableMessage[String, Array[Byte]]]
          .via(translateFlow)
          .via(processFlow(system.log, accountRegion))
          .via(publishFlow(system.log, publisher))

      broadcast ~> businessLogic ~> zip.in0
      broadcast ~> monitorFlow(system.log) ~> zip.in1

      FlowShape(broadcast.in, zip.out)
  })

  private def shardRegion(implicit system: ActorSystem): ActorRef = {
    ClusterSharding(system).start(typeName = "Account",
      entityProps = Account.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = Account.extractEntityId,
      extractShardId = Account.extractShardId)
  }

  private def kafkaConsumerConfig(implicit system: ActorSystem): Config = system.settings.config.getConfig("akka.kafka.consumer")
  private def kafkaCommitterSettings(implicit system: ActorSystem): CommitterSettings = CommitterSettings(system)

  private def publisherActor(implicit system: ActorSystem): ActorRef = {
    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = "/user/publisher",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = "publisherProxy"
    )
  }

  def partitionedStreamWithCommit(subscription: AutoSubscription)(implicit system:ActorSystem): RunnableGraph[DrainingControl[Done]] = {
      val consumerConfig = kafkaConsumerConfig
      val committerSettings = kafkaCommitterSettings
      val accountRegion = shardRegion
      val publisher = publisherActor
      val parallelism = 5
      val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(kafkaServers)
        .withGroupId("akkaSandboxCommitMultiSource2")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
        .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .withProperty(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.RoundRobinAssignor")

      val src = Consumer.committablePartitionedSource(consumerSettings, subscription)

      src.mapAsyncUnordered(parallelism) {
        case (partition, source) =>
            system.log.info(s"Source for [${partition.topic()}] partition [${partition.partition()}]")
            source.via(mainLogic(system, accountRegion, publisher)).map(_._2.committableOffset).runWith(Committer.sink(committerSettings))
      }.toMat(Sink.ignore)(DrainingControl.apply)
  }

  def streamWithCommit(implicit system:ActorSystem): RunnableGraph[NotUsed] = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val consumerConfig = kafkaConsumerConfig
      val committerSettings = kafkaCommitterSettings
      val accountRegion = shardRegion
      val publisher = publisherActor
      val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(kafkaServers)
        .withGroupId("akkaSandboxCommitSingleSource")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
        .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .withProperty(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.RoundRobinAssignor")

      val src = Consumer.committableSource(consumerSettings, Subscriptions.topics(kafkaTopic))
      val sink: Sink[ConsumerMessage.Committable, NotUsed] = Committer.flow(committerSettings).to(Sink.ignore)

        src ~> mainLogic(system, accountRegion, publisher).map(_._2.committableOffset) ~> sink

      ClosedShape
  })

  def streamNoCommit(implicit system:ActorSystem): RunnableGraph[(Consumer.Control, Future[Done])] = {

    val consumerConfig = kafkaConsumerConfig
    val accountRegion = shardRegion

    implicit val askTimeout: Timeout = 5.seconds

    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(kafkaServers)
      .withGroupId("akkaSandboxNoCommit")
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
      .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
      .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.RoundRobinAssignor")

    Consumer.plainSource(consumerSettings, Subscriptions.topics(kafkaTopic))
      .wireTap(m => system.log.info(s"message received in partition [${m.partition()}]"))
      .map(m => parseKafkaRecord(m.value()))
      .wireTap(m => system.log.info(s"message process --> command [$m]"))
      .ask[AccountEvent](parallelism = 1)(accountRegion)
      .wireTap(m => system.log.info(s"message process --> event [$m]"))
      .toMat(Sink.ignore)(Keep.both)
  }
}
