package com.flutter.akka.actors.classic

import akka.actor.PoisonPill
import akka.testkit.TestProbe
import com.flutter.akka.actors.classic.Account.{AccountBalance, AccountCredited, AccountDebited, Deposit, GetBalance, Withdraw, WithdrawalDeclined}

import scala.concurrent.duration._

class AccountSpec extends BaseActorSpec("AccountSpec") {
  val accNo:String = "acc-1"

  it should "handle account commands" in {

    val actor = system.actorOf(Account.props(), accNo)
    val probe = TestProbe()

    actor.tell(Deposit(accNo, 10), probe.ref)
    val res1 = probe.expectMsgType[AccountCredited](3.seconds)
    res1.amount should be (10)

    actor.tell(GetBalance(accNo), probe.ref)
    val res2 = probe.expectMsgType[AccountBalance](3.seconds)
    res2.totalBalance should be (10)

    actor.tell(Deposit(accNo, 12), probe.ref)
    val res3 = probe.expectMsgType[AccountCredited](3.seconds)
    res3.amount should be(12)

    actor.tell(GetBalance(accNo), probe.ref)
    val res4 = probe.expectMsgType[AccountBalance](3.seconds)
    res4.totalBalance should be(22)
    res4.transactions.size should be (2)

    probe.watch(actor)
    actor ! PoisonPill
    probe.expectTerminated(actor)
  }

  it should "handle actor recovery" in {
    val probe = TestProbe()
    val actor = system.actorOf(Account.props(), accNo)
    actor.tell(GetBalance(accNo), probe.ref)
    val res1 = probe.expectMsgType[AccountBalance](3.seconds)
    res1.totalBalance should be(22)
    res1.transactions.size should be (2)

    probe.watch(actor)
    actor ! PoisonPill
    probe.expectTerminated(actor)
  }

  it should "handle withdrawals" in {
    val probe = TestProbe()
    val actor = system.actorOf(Account.props(), accNo)
    actor.tell(GetBalance(accNo), probe.ref)
    val res1 = probe.expectMsgType[AccountBalance](3.seconds)
    res1.totalBalance should be(22)

    actor.tell(Withdraw(accNo, 5.0), probe.ref)
    val res2 = probe.expectMsgType[AccountDebited](3.seconds)
    res2.amount should be(5)

    actor.tell(Withdraw(accNo, 50.0), probe.ref)
    probe.expectMsgType[WithdrawalDeclined](3.seconds)

    actor.tell(GetBalance(accNo), probe.ref)
    val res3 = probe.expectMsgType[AccountBalance](3.seconds)
    res3.totalBalance should be(17)
    res3.transactions.size should be (4)

    probe.watch(actor)
    actor ! PoisonPill
    probe.expectTerminated(actor)
  }
}
