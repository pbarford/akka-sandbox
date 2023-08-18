package com.flutter.akka.actors.typed

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.flutter.akka.actors.typed.Account.{AccountBalance, AccountCredited, AccountEvent, Deposit, GetBalance, StopCommand}
import com.typesafe.config.ConfigFactory
import org.scalatest.{GivenWhenThen, Matchers}
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.duration._

class AccountSpec extends ScalaTestWithActorTestKit(ConfigFactory.defaultApplication()) with AnyFlatSpecLike with Matchers with GivenWhenThen {
  val accountNo = "37488374"

  it should "handle Account Commands" in {
    val probe = createTestProbe[AccountEvent]()
    val actor = spawn(Account.behavior(accountNo))
    actor ! Deposit(accountNo, 30.0, probe.ref)
    val res1 = probe.expectMessageType[AccountCredited](3.seconds)
    res1.amount should be(30.0)

    actor ! StopCommand(accountNo)
  }

  it should "handle Account Re-hydration" in {

    val probe = createTestProbe[AccountEvent]()
    val actor = spawn(Account.behavior(accountNo))
    actor ! GetBalance(accountNo, probe.ref)
    val res1 = probe.expectMessageType[AccountBalance](3.seconds)
    res1.totalBalance should be(30.0)
  }
}