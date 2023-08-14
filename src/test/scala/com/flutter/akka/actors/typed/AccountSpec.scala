package com.flutter.akka.actors.typed

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{GivenWhenThen, Matchers}
import org.scalatest.flatspec.AnyFlatSpecLike

class AccountSpec extends ScalaTestWithActorTestKit(ConfigFactory.defaultApplication()) with AnyFlatSpecLike with Matchers with GivenWhenThen {
  it should "handle Account Commands" in {
    //TODO: Implement
  }

  it should "handle Account Re-hydration" in {
    //TODO: Implement
  }
}