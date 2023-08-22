package com.flutter.akka.kafka
import com.flutter.akka.proto.Messages
import zio._
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

import java.io.IOException
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object PublishApp extends ZIOAppDefault {

  private def genGetBalanceProto(acc: String): Messages.AccountMessage = {
    val getBalance = com.flutter.akka.proto.Messages.GetBalance.newBuilder().build()
    val payload = com.flutter.akka.proto.Messages.Payload.newBuilder().setGetBalance(getBalance).build()
    com.flutter.akka.proto.Messages.AccountMessage.newBuilder().setAccountNo(acc).setPayload(payload).build()
  }

  private def genDepositProto(acc: String, amount: Double): Messages.AccountMessage = {
    val deposit = com.flutter.akka.proto.Messages.Deposit.newBuilder().setAmount(amount).build()
    val payload = com.flutter.akka.proto.Messages.Payload.newBuilder().setDeposit(deposit).build()
    com.flutter.akka.proto.Messages.AccountMessage.newBuilder().setAccountNo(acc).setPayload(payload).build()
  }

  private def genWithdrawProto(acc: String, amount: Double): Messages.AccountMessage = {
    val withdraw = com.flutter.akka.proto.Messages.Withdraw.newBuilder().setAmount(amount).build()
    val payload = com.flutter.akka.proto.Messages.Payload.newBuilder().setWithdraw(withdraw).build()
    com.flutter.akka.proto.Messages.AccountMessage.newBuilder().setAccountNo(acc).setPayload(payload).build()
  }

  private def parseInt(input: String): ZIO[Any, NumberFormatException, Int] =
    ZIO.attempt(input.toInt).refineToOrDie[NumberFormatException]

  private def parseDouble(input: String): ZIO[Any, NumberFormatException, Double] =
    ZIO.attempt(input.toDouble).refineToOrDie[NumberFormatException]

  private def readInt(value: String): ZIO[Any, IOException, Int] =
    (Console.print(value) *> Console.readLine.flatMap(parseInt))
      .retryUntil(!_.isInstanceOf[NumberFormatException])
      .refineToOrDie[IOException]

  private def readDouble(value: String): ZIO[Any, IOException, Double] =
    (Console.print(value) *> Console.readLine.flatMap(parseDouble))
      .retryUntil(!_.isInstanceOf[NumberFormatException])
      .refineToOrDie[IOException]

  private def handleDeposit()(implicit ec:ExecutionContext): ZIO[Any, Throwable, RuntimeFlags] = {
    for {
      _ <- Console.printLine("----------------------")
      _ <- Console.printLine("Deposit Message")
      _ <- Console.printLine("----------------------")
      account <- Console.readLine("Account No. : ")
      amount <- readDouble("Amount : ")
      _ <- Console.printLine(s"Publishing Deposit Message for Account No. [$account], Amount [$amount]")
      _ <- Producer.publish(account, genDepositProto(account, amount).toByteArray)
    } yield 0
  }

  private def handleWithdraw()(implicit ec:ExecutionContext): ZIO[Any, Throwable, RuntimeFlags] = {
    for {
      _ <- Console.printLine("----------------------")
      _ <- Console.printLine("Withdraw Message")
      _ <- Console.printLine("----------------------")
      account <- Console.readLine("Account No. : ")
      amount <- readDouble("Amount : ")
      _ <- Console.printLine(s"Publishing Withdraw Message for Account No. [$account], Amount [$amount]")
      _ <- Producer.publish(account, genWithdrawProto(account, amount).toByteArray)
    } yield 0
  }

  private def handleGetBalance()(implicit ec:ExecutionContext): ZIO[Any, Throwable, RuntimeFlags] = {
    for {
      _ <- Console.printLine("----------------------")
      _ <- Console.printLine("Get Balance Message")
      _ <- Console.printLine("----------------------")
      account <- Console.readLine("Account No. : ")
      _ <- Console.printLine(s"Publishing Get Balance Message for Account No. [$account]")
      _ <- Producer.publish(account, genGetBalanceProto(account).toByteArray)
    } yield 0
  }

  private def handleExit(): ZIO[Any, IOException, RuntimeFlags] = {
    for {
      _ <- Console.printLine("Exiting")
    } yield -1
  }

  private def handleOption(option:Int)(implicit ec:ExecutionContext): ZIO[Any, Throwable, RuntimeFlags] = {
    option match {
      case 1 => handleDeposit()
      case 2 => handleWithdraw()
      case 3 => handleGetBalance()
      case 4 => handleExit()
    }
  }

  private def program()(implicit ec:ExecutionContext): ZIO[Any, Throwable, RuntimeFlags] = {
    for {
      _ <- Console.printLine("----------------------")
      _ <- Console.printLine("Select Message to Send")
      _ <- Console.printLine("----------------------")
      _ <- Console.printLine("1. Deposit")
      _ <- Console.printLine("2. Withdraw")
      _ <- Console.printLine("3. Get Balance")
      _ <- Console.printLine("4. Exit")
      _ <- Console.printLine("----------------------")
      option <- readInt("Choose Option : ").repeatUntil(op => op > 0 && op <= 4)
      res <- handleOption(option)
    } yield  res
  }

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
    program().repeatUntil(_ == -1)

  }
}
