organization in ThisBuild := "org.jbok"

name := "jbok"

description := "Just a Bunch Of Keys"

scalaVersion in ThisBuild := "2.12.4"

cancelable in Global := true

lazy val V = new {
  val circe = "0.9.1"
  val akka = "2.5.11"
  val akkaHttp = "10.1.0"
  val tsec = "0.0.1-M11"
  val http4s = "0.18.12"
}

lazy val fs2 = Seq(
  "co.fs2" %% "fs2-core" % "0.10.4"
)

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
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
  "org.log4s" %% "log4s" % "1.6.1"
)

lazy val tsec = Seq(
  "io.github.jmcardon" %% "tsec-common" % V.tsec,
  "io.github.jmcardon" %% "tsec-hash-jca" % V.tsec,
  "io.github.jmcardon" %% "tsec-hash-bouncy" % V.tsec,
  "io.github.jmcardon" %% "tsec-signatures" % V.tsec
)

lazy val cats = Seq(
  "org.typelevel" %% "cats-core" % "1.1.0",
  "org.typelevel" %% "cats-effect" % "1.0.0-RC"
)

lazy val http4s = Seq(
  "org.http4s" %% "http4s-core",
  "org.http4s" %% "http4s-blaze-server",
  "org.http4s" %% "http4s-blaze-client" ,
  "org.http4s" %% "http4s-circe",
  "org.http4s" %% "http4s-dsl"
).map(_ % V.http4s)

lazy val monix = Seq(
  "io.monix" %% "monix" % "3.0.0-RC1"
)

lazy val commonSettings = Seq(
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"),
  scalacOpts
)

lazy val jbok = project
  .in(file("."))
  .aggregate(core)

lazy val common = project
  .settings(commonSettings)
  .settings(
    name := "jbok-common",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies ++= logging ++ tests ++ cats ++ fs2 ++ Seq(
      "org.scala-graph" %% "graph-core" % "1.12.5",
      "org.scala-graph" %% "graph-dot" % "1.12.1",
      "com.github.mpilquist" %% "simulacrum" % "0.12.0",
      "com.beachape" %% "enumeratum" % "1.5.13",
      "com.beachape" %% "enumeratum-circe" % "1.5.13"
    )
  )

lazy val core = project
  .settings(commonSettings)
  .settings(
    name := "jbok-core",
    libraryDependencies ++= http4s ++ circe ++ Seq(
     "com.github.pathikrit" %% "better-files" % "3.5.0"
    )
  )
  .dependsOn(common % CompileAndTest, crypto, p2p, rpc)

lazy val crypto = project
  .settings(commonSettings)
  .settings(
    name := "jbok-crypto",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies ++= tsec ++ circe ++ Seq(
      "org.scodec" %% "scodec-bits" % "1.1.5",
      "org.scodec" %% "scodec-core" % "1.10.3",
      "org.scorexfoundation" %% "scrypto" % "2.0.5"
    )
  )
  .dependsOn(common % CompileAndTest, codec, persistent)

lazy val p2p = project
  .settings(commonSettings)
  .settings(
    name := "jbok-p2p",
    libraryDependencies ++= akka ++ Seq(
      "com.lihaoyi" %% "fastparse" % "1.0.0"
    )
  )
  .dependsOn(common % CompileAndTest, crypto)

lazy val codec = project
  .settings(commonSettings)
  .settings(
    name := "jbok-codec",
    libraryDependencies ++= circe ++ Seq(
      "org.scodec" %% "scodec-bits" % "1.1.5",
      "org.scodec" %% "scodec-core" % "1.10.3",
    )
  )
  .dependsOn(common % CompileAndTest)

lazy val examples = project
  .settings(commonSettings)
  .settings(
    name := "jbok-examples"
  )
  .dependsOn(core % CompileAndTest)

lazy val simulations = project
  .settings(commonSettings)
  .settings(
    name := "jbok-simulations",
    libraryDependencies ++= monix
  )
  .dependsOn(core % CompileAndTest)

lazy val app = project
  .settings(commonSettings)
  .settings(
    name := "jbok-app",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.thoughtworks.binding" %%% "dom" % "11.0.1",
      "com.thoughtworks.binding" %%% "route" % "11.0.1"
    )
  )
  .enablePlugins(ScalaJSPlugin)

lazy val persistent = project
  .settings(commonSettings)
  .settings(
    name := "jbok-persistent",
    libraryDependencies ++= fs2 ++ Seq(
      "org.scodec" %% "scodec-bits" % "1.1.5",
      "org.scodec" %% "scodec-core" % "1.10.3",
      "org.iq80.leveldb" % "leveldb" % "0.10",
      "io.monix" %% "monix" % "3.0.0-RC1",
      "org.scalacheck" %% "scalacheck" % "1.13.4"
    )
  )
  .dependsOn(common % CompileAndTest)

lazy val rpc = project
  .settings(commonSettings)
  .settings(
    name := "jbok-rpc",
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
    ),
    libraryDependencies ++= circe ++ http4s ++ monix ++ akka ++ Seq(
      "com.spinoco" %% "fs2-http" % "0.3.0",
      "com.github.zainab-ali" %% "fs2-reactive-streams" % "0.5.1"
    )
  )
  .dependsOn(common % CompileAndTest, codec)

lazy val benchmark = project
  .settings(commonSettings)
  .settings(
    name := "jbok-benchmark"
  )
  .enablePlugins(JmhPlugin)
  .dependsOn(persistent, rpc)

lazy val CompileAndTest = "compile->compile;test->test"

publishMavenStyle := true

publishArtifact in Test := false

lazy val scalacOpts = scalacOptions := Seq(
  "-unchecked",
  "-feature",
  "-deprecation",
  "-encoding",
  "utf8",
  "-Ywarn-inaccessible",
  "-Ywarn-nullary-override",
  "-Ypartial-unification",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps"
)