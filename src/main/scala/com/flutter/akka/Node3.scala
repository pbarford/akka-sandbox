package com.flutter.akka

import akka.actor.ActorSystem
import com.flutter.akka.streams.AccountStream

object Node3 extends App with Node {

  private implicit val system: ActorSystem = akkaSystem(2553)
  AccountStream.partitionedStreamWithCommit.run()

}
