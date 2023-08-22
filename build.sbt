import sbt._

ThisBuild / organization := "com.flutter"
ThisBuild / scalacOptions ++= Compiler.scalacOptions

val scalaV:String = "2.13.2"
scalaVersion := scalaV

val protobufJavaVersion = "3.21.6"

lazy val protoSrc = (project in file("proto-src"))
  .settings(
    name := "akka-sandbox-proto",
    scalaVersion := scalaV,
    crossScalaVersions := List(scalaV),
    unmanagedResourceDirectories in Compile += (sourceDirectory in ProtobufConfig).value,
    libraryDependencies := Dependency.protobuf,
    version in ProtobufConfig := protobufJavaVersion
  )
  .enablePlugins(ProtobufPlugin)

lazy val root = (project in file("."))
  .settings(
    name := "akka-sandbox",
    scalaVersion := scalaV,
    crossScalaVersions := List(scalaV),
    libraryDependencies := Dependency.rootDeps,
    version in ProtobufConfig := protobufJavaVersion
  )
  .dependsOn(protoSrc)
  .aggregate(protoSrc)
