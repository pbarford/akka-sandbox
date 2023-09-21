package com.flutter.akka

import akka.actor.ActorSystem
import com.flutter.akka.streams.AccountStream

object Node2 extends App with Node {

  private implicit val system: ActorSystem = akkaSystem(2552)
  AccountStream.partitionedStreamWithCommit.run()

}
