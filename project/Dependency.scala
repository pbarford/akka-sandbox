import sbt._

object Dependency {

  val akkaVersion:String = "2.8.2"

  val protobuf: Seq[ModuleID] = Seq(
    "com.google.protobuf" % "protobuf-java" % "3.21.6" % "protobuf",
    "com.google.protobuf" % "protobuf-java-util" % "3.21.6"
  )

  lazy val scalaLib = Seq(
    "org.scala-lang" % "scala-library" % "2.13.2"
  )

  val rootDeps: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % "10.1.10",
    "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.2",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "org.scalatest" %% "scalatest" % "3.1.1",
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.typelevel" %% "cats-core" % "2.1.1",
    "org.typelevel" %% "cats-effect" % "2.1.3",
    "org.apache.httpcomponents" % "httpclient" % "4.5.14",
    "dev.zio" %% "zio" % "2.0.2"
  )
}