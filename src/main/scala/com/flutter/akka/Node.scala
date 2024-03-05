package com.flutter.akka

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, path}
import akka.http.scaladsl.server.Route
import com.flutter.akka.actors.classic.Account.Alert
import com.flutter.akka.actors.classic.Publisher
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.ExecutionContextExecutor

trait Node {
  val kafkaTopic = "PartitionedTopic"

  val accountConsumerGroupId = "AccountStreamConsumerGroup"
  val accountSystemSeedNodes = List("akka://AccountStream@127.0.0.1:2551","akka://AccountStream@127.0.0.1:2552","akka://AccountStream@127.0.0.1:2553")

  def akkaSystem(name:String, port:Int, seedNodes:List[String]) = {
    implicit val system = ActorSystem(name, akkaConfig(port, seedNodes))
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Publisher.props,
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)),
      name = "publisher")

    val mediator: ActorRef = DistributedPubSub(system).mediator
    val route: Route = path("alert") {
      mediator ! Publish("alerts", Alert("Alert from HTTP"))
      complete("OK")
    }
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    val server = Http().newServerAt("localhost", port + 7000).bind(route)
    server.map { _ =>
      println(s"Successfully started on localhost: ${port + 7000}")
    } recover {
      case ex =>
        println("Failed to start the server due to: " + ex.getMessage)
    }
    system
  }

  private def akkaConfig(port: Int, seedNodes:List[String]): Config = {
    val nodes = seedNodes.foldLeft("") ((acc,el) => if(acc.isEmpty) "\"" +el + "\"" else acc + ",\"" + el +"\"")
    val nodeConfig = ConfigFactory.parseString(s"akka.remote.artery.canonical.port=$port,akka.cluster.seed-nodes=[$nodes]")
    nodeConfig.withFallback(ConfigFactory.load("application-test.conf"))
  }

}
