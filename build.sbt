name := "akka-sandbox"

version := "0.1"

val scalaV:String = "2.13.2"
scalaVersion := scalaV


val protobufJavaVersion = "3.21.6"

ThisBuild / scalacOptions ++= Seq(
  "-Ywarn-dead-code",
  "-Ywarn-unused:imports,params,privates"
)

lazy val protosrc = (project in file("proto-src"))
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
  .enablePlugins(ProtobufPlugin)
  .dependsOn(protosrc)
  .aggregate(protosrc)
