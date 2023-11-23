package com.flutter.akka

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.flutter.akka.actors.classic.Publisher
import com.typesafe.config.{Config, ConfigFactory}

trait Node {

  val accountSystemSeedNodes = List("akka://AccountStream@127.0.0.1:2551","akka://AccountStream@127.0.0.1:2552","akka://AccountStream@127.0.0.1:2553")

  def akkaSystem(name:String, port:Int, seedNodes:List[String]) = {
    val system = ActorSystem(name, akkaConfig(port, seedNodes))
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Publisher.props,
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)),
      name = "publisher")
    system
  }

  private def akkaConfig(port: Int, seedNodes:List[String]): Config = {
    val nodes = seedNodes.foldLeft("") ((acc,el) => if(acc.isBlank) "\"" +el + "\"" else acc + ",\"" + el +"\"")
    val nodeConfig = ConfigFactory.parseString(s"akka.remote.artery.canonical.port=$port,akka.cluster.seed-nodes=[$nodes]")
    nodeConfig.withFallback(ConfigFactory.load("application-test.conf"))
  }

}
