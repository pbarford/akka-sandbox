package com.flutter.akka.kafka
import cats.effect.IO
import com.flutter.akka.proto.Messages

object MessagePublish  extends App {

  private def putStrLn(value: String):IO[Unit] = IO(println(value))
  private val readString = IO(scala.io.StdIn.readLine())
  private val readDouble = IO(scala.io.StdIn.readDouble())
  private val readInt = IO(scala.io.StdIn.readInt())

  private def handleOption(option:Int):IO[Unit] = {
    option match {
      case 1 => handleDeposit()
      case 2 => handleWithdraw()
      case 3 => handleGetBalance()
      case _ => handleInvalidOption()
    }
  }

  private def genGetBalanceProto(acc:String): Messages.GetBalance = {
    com.flutter.akka.proto.Messages.GetBalance.newBuilder().setAccountNo(acc).build()
  }

  private def genDepositProto(acc: String, amount:Double): Messages.Deposit = {
    com.flutter.akka.proto.Messages.Deposit.newBuilder().setAccountNo(acc).setAmount(amount).build()
  }

  private def genWithdrawProto(acc: String, amount: Double): Messages.Withdraw = {
    com.flutter.akka.proto.Messages.Withdraw.newBuilder().setAccountNo(acc).setAmount(amount).build()
  }

  private def handleInvalidOption():IO[Unit] = {
    for {
      _ <- putStrLn("----------------------")
      _ <- putStrLn("Invalid Option")
      _ <- putStrLn("----------------------")
    } yield()
  }

  private def handleDeposit():IO[Unit] = {
    for {
      _ <- putStrLn("----------------------")
      _ <- putStrLn("Deposit Message")
      _ <- putStrLn("----------------------")
      _ <- putStrLn("Account No. : ")
      account <- readString
      _ <- putStrLn("Amount : ")
      amount <- readDouble
      _ <- putStrLn(s"Send Deposit Message for Account No. [$account], Amount [$amount]")
      message = genDepositProto(account, amount)
    } yield ()
  }

  private def handleWithdraw():IO[Unit] = {
    for {
      _ <- putStrLn("----------------------")
      _ <- putStrLn("Withdraw Message")
      _ <- putStrLn("----------------------")
      _ <- putStrLn("Account No.")
      account <- readString
      _ <- putStrLn("Amount : ")
      amount <- readDouble
      _ <- putStrLn(s"Send Withdraw Message for Account No. [$account], Amount [$amount]")
      message = genWithdrawProto(account, amount)
    } yield ()
  }

  private def handleGetBalance():IO[Unit] = {
    for {
      _ <- putStrLn("----------------------")
      _ <- putStrLn("Get Balance Message")
      _ <- putStrLn("----------------------")
      _ <- putStrLn("Account No.")
      account <- readString
      _ <- putStrLn(s"Send Get Balance Message for Account No. [$account]")
      message = genGetBalanceProto(account)
    } yield ()
  }

  private val program = for {
    _ <- putStrLn("----------------------")
    _ <- putStrLn("Select Message to Send")
    _ <- putStrLn("----------------------")
    _ <- putStrLn("1. Deposit")
    _ <- putStrLn("2. Withdraw")
    _ <- putStrLn("3. Get Balance")
    _ <- putStrLn("----------------------")
    _ <- putStrLn("Choose : ")
    option <- readInt
    _ <- handleOption(option)
  } yield ()

  program.unsafeRunSync()
}
