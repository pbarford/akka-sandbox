package com.flutter.akka

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.flutter.akka.actors.classic.Publisher
import com.typesafe.config.{Config, ConfigFactory}

trait Node {

  def akkaSystem(port:Int): ActorSystem = {
    val system = ActorSystem("AccountStream", akkaConfig(port))
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Publisher.props,
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)),
      name = "publisher")
    system
  }

  private def akkaConfig(port:Int): Config = {
    val nodeConfig = ConfigFactory.parseString(s"akka.remote.artery.canonical.port=$port")
     nodeConfig.withFallback(ConfigFactory.defaultApplication())
  }

}
