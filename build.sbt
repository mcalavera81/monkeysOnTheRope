name := """minimal-akka-scala-seed"""

version := "1.0"

scalaVersion := "2.11.6"

scalacOptions += "-feature"
scalacOptions += "-language:postfixOps"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.11",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.miguno.akka" %% "akka-mock-scheduler" % "0.4.0",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test")


mainClass in assembly := Some("com.example.ApplicationMain")

fork in run := true