organization in ThisBuild := "org.jbok"

name := "jbok"

description := "Just a Bunch Of Keys"

scalaVersion in ThisBuild := "2.12.3"

cancelable in Global := true

lazy val tests = Seq(
  "org.scalatest"  %% "scalatest"  % "3.0.5",
  "org.scalacheck" %% "scalacheck" % "1.13.4"
).map(_ % "test")

lazy val logging = Seq(
  "ch.qos.logback"             % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging"  % "3.8.0"
)

lazy val jbok = project
  .in(file("."))
  .aggregate(p2p)

lazy val p2p = project
  .settings(
    name := "jbok-p2p",
    libraryDependencies ++= logging ++ tests ++ Seq(
      "com.lihaoyi"   %% "fastparse" % "1.0.0",
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
