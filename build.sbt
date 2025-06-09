name := "angermess-backend"

version := "0.1"

scalaVersion := "3.3.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.5.3",
  "com.typesafe.akka" %% "akka-stream" % "2.8.8",
  "org.mongodb" % "mongodb-driver-sync" % "5.5.0",
  "ch.qos.logback" % "logback-classic" % "1.5.18"
)