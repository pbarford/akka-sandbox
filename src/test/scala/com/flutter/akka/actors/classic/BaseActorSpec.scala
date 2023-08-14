package com.flutter.akka.actors.classic

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class BaseActorSpec(_system: ActorSystem) extends TestKit(_system)
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers {

  def this(actorSystemName: String) = {
    this(ActorSystem(actorSystemName))
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}