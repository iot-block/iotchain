import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

organization in ThisBuild := "org.jbok"

name := "jbok"

description := "Just a Bunch Of Keys"

scalaVersion in ThisBuild := "2.12.4"

cancelable in Global := true

lazy val V = new {
  val circe = "0.9.1"
  val tsec = "0.0.1-M11"
  val http4s = "0.18.12"
}

lazy val fs2 = Seq(
  "co.fs2" %% "fs2-core" % "0.10.4"
)

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

lazy val http4s = Seq(
  "org.http4s" %% "http4s-core",
  "org.http4s" %% "http4s-blaze-server",
  "org.http4s" %% "http4s-blaze-client",
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
  fork in test := false,
  fork in run := true,
  parallelExecution in test := false,
  scalacOpts
)

lazy val jbok = project
  .in(file("."))
  .aggregate(networkJS, networkJVM, appJS, appJVM)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-common",
    libraryDependencies ++= logging ++ Seq(
      "org.typelevel" %%% "cats-core" % "1.1.0",
      "org.typelevel" %%% "cats-effect" % "1.0.0-RC",
      "io.circe" %%% "circe-core" % V.circe,
      "io.circe" %%% "circe-generic" % V.circe,
      "io.circe" %%% "circe-parser" % V.circe,
      "org.scala-graph" %%% "graph-core" % "1.12.5",
      "org.scala-graph" %% "graph-dot" % "1.12.1",
      "com.github.mpilquist" %%% "simulacrum" % "0.12.0",
      "com.beachape" %%% "enumeratum" % "1.5.13",
      "com.beachape" %%% "enumeratum-circe" % "1.5.13",
      "co.fs2" %%% "fs2-core" % "0.10.4",
      "org.scodec" %%% "scodec-bits" % "1.1.5",
      "org.scodec" %%% "scodec-core" % "1.10.3",
      "org.scalatest" %%% "scalatest" % "3.0.5" % Test,
      "org.scalacheck" %%% "scalacheck" % "1.13.4" % Test
    )
  )

lazy val commonJS = common.js
lazy val commonJVM = common.jvm

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-core",
    libraryDependencies ++= http4s ++ Seq(
      "com.github.pathikrit" %% "better-files" % "3.5.0"
    )
  )
  .dependsOn(common % CompileAndTest, crypto, p2p, persistent)

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

lazy val crypto = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .jsSettings(
    npmDependencies in Compile ++= Seq(
      "elliptic" -> "6.4.0"
    ),
    skip in packageJSDependencies := false,
    jsEnv in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .settings(
    name := "jbok-crypto",
    libraryDependencies ++= tsec ++ Seq(
      "org.scorexfoundation" %% "scrypto" % "2.0.5",
      "org.bouncycastle" % "bcprov-jdk15on" % "1.59",
      "net.i2p.crypto" % "eddsa" % "0.3.0"
    )
  )
  .dependsOn(common % CompileAndTest, codec, persistent)

lazy val cryptoJS = crypto.js.enablePlugins(ScalaJSBundlerPlugin)
lazy val cryptoJVM = crypto.jvm

lazy val p2p = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-p2p"
  )
  .dependsOn(common % CompileAndTest, network, persistent, codec)

lazy val p2pJS = p2p.js
lazy val p2pJVM = p2p.jvm

lazy val codec = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-codec"
  )
  .dependsOn(common % CompileAndTest)

lazy val codecJS = codec.js
lazy val codecJVM = codec.jvm

//lazy val examples = project
//  .settings(commonSettings)
//  .settings(
//    name := "jbok-examples"
//  )
//  .dependsOn(core % CompileAndTest)

lazy val simulations = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-simulations",
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % "0.4.0-RC1"
    )
  )
  .dependsOn(common % CompileAndTest, core)
  .enablePlugins(JavaAppPackaging)

lazy val simJS = simulations.js
lazy val simJVM = simulations.jvm

lazy val commonJsSettings = Seq(
  scalaJSUseMainModuleInitializer := true,
  jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
  fork in Test := false,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.2"
  )
)

lazy val app = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-app",
    libraryDependencies ++= Seq(
      "com.thoughtworks.binding" %%% "binding" % "11.0.1",
      "com.lihaoyi" %%% "upickle" % "0.6.6",
      "com.lihaoyi" %%% "scalatags" % "0.6.7",
      "com.monovore" %% "decline" % "0.4.0-RC1"
    )
  )
  .dependsOn(network, simulations)

lazy val appJS = app.js
lazy val appJVM = app.jvm

lazy val macros = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-macros"
  )
  .dependsOn(common % CompileAndTest, codec)

lazy val macrosJS = macros.js
lazy val macrosJVM = macros.jvm

lazy val network = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .settings(
    name := "jbok-network",
    libraryDependencies ++= logging
  )
  .jsSettings(commonJsSettings)
  .jvmSettings(
    libraryDependencies ++= http4s ++ logging ++ Seq(
      "com.spinoco" %% "fs2-http" % "0.3.0"
    )
  )
  .dependsOn(common % CompileAndTest, macros)

lazy val networkJS = network.js
lazy val networkJVM = network.jvm

lazy val persistent = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-persistent",
    libraryDependencies ++= Seq(
      "org.iq80.leveldb" % "leveldb" % "0.10",
      "io.monix" %% "monix" % "3.0.0-RC1"
    )
  )
  .dependsOn(common % CompileAndTest, codec)

lazy val persistentJS = persistent.js
lazy val persistentJVM = persistent.jvm

lazy val benchmark = project
  .settings(commonSettings)
  .settings(
    name := "jbok-benchmark"
  )
  .enablePlugins(JmhPlugin)
  .dependsOn(persistentJVM)

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
