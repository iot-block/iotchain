import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val jbok = project
  .in(file("."))
  .aggregate(
    common.js,
    common.jvm,
    codec.js,
    codec.jvm,
    persistent.js,
    persistent.jvm,
    crypto.js,
    crypto.jvm,
    network.js,
    network.jvm,
    core.js,
    core.jvm,
    sdk.jvm,
    app.js,
    app.jvm,
    benchmark
  )
  .settings(Publish.noPublishSettings)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(Settings.common ++ Libs.common)
  .settings(name := "jbok-common")
  .jvmSettings(Settings.jvmCommon)
  .jsSettings(ScalaJS.common)
  .dependsOn(macros)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(Settings.common ++ Libs.fastparse)
  .settings(name := "jbok-core")
  .jvmSettings(Settings.jvmCommon)
  .jsSettings(ScalaJS.common)
  .dependsOn(Seq(common, codec, crypto, network, persistent).map(_ % CompileAndTest): _*)

lazy val crypto = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(Settings.common ++ Libs.tsec)
  .settings(name := "jbok-crypto")
  .jvmSettings(Settings.jvmCommon)
  .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin))
  .jsSettings(ScalaJS.common ++ Libs.js.crypto)
  .dependsOn(common % CompileAndTest, codec, persistent)

lazy val codec = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(Settings.common)
  .settings(name := "jbok-codec")
  .jvmSettings(Settings.jvmCommon)
  .jsSettings(ScalaJS.common)
  .dependsOn(common % CompileAndTest)

lazy val app = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .jvmConfigure(_.enablePlugins(JavaAppPackaging, AshScriptPlugin, WebScalaJSBundlerPlugin))
  .settings(Settings.common)
  .settings(name := "jbok-app")
  .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb))
  .jsSettings(ScalaJS.common ++ ScalaJS.webpackSettings)
  .jvmSettings(Settings.jvmCommon ++ DockerSettings.settings ++ Libs.sql ++ Libs.terminal)
  .dependsOn(core % CompileAndTest, common % CompileAndTest, sdk % CompileAndTest)

// for integrating with sbt-web
lazy val appJS = app.js.settings(
  scalaJSUseMainModuleInitializer := true
)

lazy val appJVM = app.jvm.settings(
  scalaJSProjects := Seq(appJS, sdk.js),
  pipelineStages in Assets := Seq(scalaJSPipeline),
  javaOptions in Universal ++= Seq(
    "-J-Xms2g",
    "-J-Xmx4g",
    "-J-XX:+HeapDumpOnOutOfMemoryError"
  )
)

lazy val sdk = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(Settings.common)
  .settings(name := "jbok-sdk")
  .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin))
  .jsSettings(ScalaJS.common ++ ScalaJS.webpackSettings)
  .dependsOn(core % CompileAndTest, common % CompileAndTest)

lazy val macros = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(Settings.common)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % Versions.catsEffect,
      "com.outr"      %%% "scribe"      % Versions.scribe
    ))
  .settings(name := "jbok-macros")

lazy val network = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .settings(Settings.common ++ Libs.http4s ++ Libs.network)
  .settings(name := "jbok-network")
  .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin))
  .jsSettings(ScalaJS.common ++ Libs.js.network)
  .jvmSettings(Settings.jvmCommon)
  .dependsOn(common % CompileAndTest, crypto)

lazy val persistent = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(Settings.common ++ Libs.kv)
  .settings(name := "jbok-persistent")
  .jsSettings(ScalaJS.common ++ Libs.js.common)
  .jvmSettings(Settings.jvmCommon)
  .dependsOn(common % CompileAndTest, codec)

lazy val benchmark = project
  .settings(Settings.common ++ Settings.jvmCommon ++ Publish.noPublishSettings ++ Benchmark.settings)
  .settings(name := "jbok-benchmark")
  .enablePlugins(JmhPlugin)
  .dependsOn(core.jvm % CompileAndTest, persistent.jvm)

lazy val docs = project
  .settings(Settings.common ++ Settings.jvmCommon ++ Publish.noPublishSettings ++ Docs.settings)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(TutPlugin)
  .dependsOn(core.jvm)

lazy val CompileAndTest = "compile->compile;test->test"
