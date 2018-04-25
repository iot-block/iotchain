organization in ThisBuild := "org.jbok"

name := "jbok"

description := "Just a Bunch Of Keys"

scalaVersion in ThisBuild := "2.12.3"

cancelable in Global := true

lazy val V = new {
  val circe = "0.9.1"
  val akka = "2.5.11"
  val akkaHttp = "10.1.0"
  val tsec = "0.0.1-M11"
}

lazy val circe = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % V.circe)

lazy val akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % V.akka,
  "com.typesafe.akka" %% "akka-stream" % V.akka,
  "com.typesafe.akka" %% "akka-slf4j" % V.akka,
  "com.typesafe.akka" %% "akka-http" % V.akkaHttp,
  "com.typesafe.akka" %% "akka-testkit" % V.akka % "test",
  "com.typesafe.akka" %% "akka-stream-testkit" % V.akka % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % V.akkaHttp % "test"
)

lazy val tests = Seq(
  "org.scalatest" %% "scalatest" % "3.0.5",
  "org.scalacheck" %% "scalacheck" % "1.13.4"
).map(_ % "test")

lazy val logging = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0"
)

lazy val tsec = Seq(
  "io.github.jmcardon" %% "tsec-common" % V.tsec,
  "io.github.jmcardon" %% "tsec-hash-jca" % V.tsec,
  "io.github.jmcardon" %% "tsec-hash-bouncy" % V.tsec,
  "io.github.jmcardon" %% "tsec-signatures" % V.tsec
)

lazy val jbok = project
  .in(file("."))
  .aggregate(p2p)

lazy val crypto = project
  .settings(
    name := "jbok-crypto",
    libraryDependencies ++= logging ++ tests ++ tsec ++ Seq(
      "org.scodec" %% "scodec-bits" % "1.1.5",
      "org.scodec" %% "scodec-core" % "1.10.3"
    )
  )

lazy val p2p = project
  .settings(
    name := "jbok-p2p",
    libraryDependencies ++= logging ++ tests ++ akka ++ Seq(
      "com.lihaoyi" %% "fastparse" % "1.0.0",
      "org.typelevel" %% "cats-core" % "1.1.0"
    )
  )

lazy val CompileAndTest = "compile->compile;test->test"

publishMavenStyle := true

publishArtifact in Test := false

scalacOptions in ThisBuild ++= Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8"
)
