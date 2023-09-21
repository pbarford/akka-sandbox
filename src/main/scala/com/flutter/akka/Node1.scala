package com.flutter.akka

import akka.actor.ActorSystem
import com.flutter.akka.streams.AccountStream

object Node1 extends App with Node {

  private implicit val system: ActorSystem = akkaSystem(2551)
  AccountStream.partitionedStreamWithCommit.run()

}
