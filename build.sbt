import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import scalajsbundler.util.JSON

organization in ThisBuild := "org.jbok"

name := "jbok"

description := "Just a Bunch Of Keys"

scalaVersion in ThisBuild := "2.12.6"

cancelable in Global := true

lazy val contributors = Map(
  "blazingsiyan" -> "siyan"
)

lazy val V = new {
  val circe           = "0.9.1"
  val tsec            = "0.0.1-RC1"
  val http4s          = "0.20.0-M4"
  val fs2             = "1.0.2"
  val catsEffect      = "1.1.0"
  val catsCollections = "0.7.0"
  val scalacache      = "0.26.0"
  val dropwizard      = "4.0.3"
  val prometheus      = "0.6.0"
  val fs2redis        = "0.5.1"
}

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
    app.jvm
  )
  .settings(noPublishSettings)

// copy from doobie
lazy val jbokWarts =
  Warts.allBut(
    Wart.Any, // false positives
    Wart.ArrayEquals, // false positives
    Wart.Nothing, // false positives
    Wart.Null, // Java API under the hood; we have to deal with null
    Wart.Product, // false positives
    Wart.Serializable, // false positives
    Wart.ImplicitConversion, // we know what we're doing
    Wart.Throw, // TODO: switch to ApplicativeError.fail in most places
    Wart.PublicInference, // fails https://github.com/wartremover/wartremover/issues/398
    Wart.ImplicitParameter, // only used for Pos, but evidently can't be suppressed
    Wart.Equals,
    Wart.MutableDataStructures,
    Wart.DefaultArguments,
    Wart.Var,
    Wart.ToString,
    Wart.NonUnitStatements,
    Wart.Overloading,
    Wart.AsInstanceOf,
    Wart.Recursion,
    Wart.JavaConversions,
    Wart.Option2Iterable,
    Wart.TraversableOps // TODO
  )

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-common",
    libraryDependencies ++= Seq(
      // typelevel
      "org.typelevel" %%% "cats-effect"          % V.catsEffect,
      "org.typelevel" %% "cats-collections-core" % V.catsCollections,
      "co.fs2"        %%% "fs2-core"             % V.fs2,
      "co.fs2"        %% "fs2-io"                % V.fs2,
      "org.typelevel" %%% "spire"                % "0.16.0",
      // json
      "io.circe" %%% "circe-core"       % V.circe,
      "io.circe" %%% "circe-generic"    % V.circe,
      "io.circe" %%% "circe-parser"     % V.circe,
      "io.circe" %%% "circe-derivation" % "0.9.0-M5",
      // binary
      "org.scodec" %%% "scodec-bits"  % "1.1.6",
      "org.scodec" %%% "scodec-core"  % "1.10.3",
      "org.scodec" %% "scodec-stream" % "1.2.0",
      // graph
      "org.scala-graph" %%% "graph-core" % "1.12.5",
      "org.scala-graph" %% "graph-dot"   % "1.12.1",
      // enum
      "com.beachape" %%% "enumeratum"       % "1.5.13",
      "com.beachape" %%% "enumeratum-circe" % "1.5.13",
      // logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.log4s"      %% "log4s"          % "1.6.1",
      "com.outr"       %%% "scribe"        % "2.7.0",
      // files
      "com.github.pathikrit" %% "better-files" % "3.5.0",
      // test
      "org.scalatest"  %%% "scalatest"  % "3.0.5"  % Test,
      "org.scalacheck" %%% "scalacheck" % "1.13.4" % Test,
      // macro
      "com.propensive" %%% "magnolia" % "0.10.0",
      // scalajs-stubs
      "org.scala-js" %% "scalajs-stubs" % "0.6.26"
    ) ++ dropwizard ++ prometheus
  )

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-core"
  )
  .dependsOn(Seq(common, codec, crypto, network, persistent).map(_ % CompileAndTest): _*)

lazy val pow = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-pow"
  )
  .dependsOn(common % CompileAndTest, core % CompileAndTest)

lazy val crypto = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin))
  .jsSettings(
    npmDependencies in Compile ++= Seq(
      "elliptic"  -> "6.4.0",
      "crypto-js" -> "3.1.9-1"
    ),
    // https://github.com/indutny/elliptic/issues/149
    jsEnv in Test := new org.scalajs.jsenv.nodejs.NodeJSEnv()
  )
  .settings(
    name := "jbok-crypto",
    libraryDependencies ++= tsec
  )
  .dependsOn(common % CompileAndTest, codec, persistent)

lazy val codec = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-codec"
  )
  .dependsOn(common % CompileAndTest)

lazy val app = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jvmConfigure(_.enablePlugins(JavaAppPackaging, AshScriptPlugin, WebScalaJSBundlerPlugin))
  .jsSettings(commonJsSettings)
  .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb))
  .settings(
    name := "jbok-app",
    packageName in Docker := "jbok",
    dockerBaseImage := "openjdk:8-jre-alpine"
  )
  .jsSettings(
    useYarn := true,
    additionalNpmConfig in Compile := Map(
      "license"     -> JSON.str("MIT"),
      "name"        -> JSON.str("JBOK"),
      "description" -> JSON.str("JBOK"),
      "version"     -> JSON.str("0.0.1"),
      "author"      -> JSON.str("JBOK authors"),
      "repository" -> JSON.obj(
        "type" -> JSON.str("git"),
        "url"  -> JSON.str("https://github.com/c-block/jbok.git")
      ),
      "build" -> JSON.obj(
        "appId" -> JSON.str("org.jbok.app")
      ),
      "scripts" -> JSON.obj(
        "pack" -> JSON.str("electron-builder --dir"),
        "dist" -> JSON.str("electron-builder")
      )
    ),
    npmDevDependencies in Compile ++= Seq(
      "file-loader"         -> "1.1.11",
      "style-loader"        -> "0.20.3",
      "css-loader"          -> "0.28.11",
      "html-webpack-plugin" -> "3.2.0",
      "copy-webpack-plugin" -> "4.5.1",
      "webpack-merge"       -> "4.1.2",
      "electron-builder"    -> "20.28.4"
    ),
    version in webpack := "4.8.1",
    version in startWebpackDevServer := "3.1.4",
    webpackConfigFile := Some((resourceDirectory in Compile).value / "webpack.config.js"),
    webpackBundlingMode := BundlingMode.LibraryAndApplication()
  )
  .dependsOn(core % CompileAndTest, common % CompileAndTest, sdk % CompileAndTest)

// for integrating with sbt-web
lazy val appJS = app.js
lazy val appJVM = app.jvm.settings(
  scalaJSProjects := Seq(appJS, sdk.js),
  pipelineStages in Assets := Seq(scalaJSPipeline),
  isDevMode in scalaJSPipeline := true,
  javaOptions in Universal ++= Seq(
    "-J-Xms2g",
    "-J-Xmx4g"
  )
)

lazy val sdk = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .jsSettings(scalaJSUseMainModuleInitializer := false)
  .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin))
  .settings(
    name := "jbok-sdk"
  )
  .jsSettings(
    useYarn := true,
    additionalNpmConfig in Compile := Map(
      "license"     -> JSON.str("MIT"),
      "name"        -> JSON.str("jbok-sdk"),
      "description" -> JSON.str("JBOK-SDK"),
      "version"     -> JSON.str("0.0.1"),
      "author"      -> JSON.str("JBOK authors"),
      "repository" -> JSON.obj(
        "type" -> JSON.str("git"),
        "url"  -> JSON.str("https://github.com/c-block/jbok.git")
      ),
      "build" -> JSON.obj(
        "appId" -> JSON.str("org.jbok.sdk")
      )
    ),
    npmDevDependencies in Compile ++= Seq(
      "file-loader"         -> "1.1.11",
      "html-webpack-plugin" -> "3.2.0",
      "copy-webpack-plugin" -> "4.5.1",
      "webpack-merge"       -> "4.1.2"
    ),
    version in webpack := "4.8.1",
    version in startWebpackDevServer := "3.1.4",
    webpackConfigFile := Some((resourceDirectory in Compile).value / "webpack.config.js"),
    jsEnv in Test := new org.scalajs.jsenv.nodejs.NodeJSEnv()
  )
  .dependsOn(core % CompileAndTest, common % CompileAndTest)

lazy val macros = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-macros"
  )
  .dependsOn(common % CompileAndTest, codec)

lazy val network = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .jsSettings(
    npmDependencies in Compile ++= Seq(
      "ws"            -> "6.1.2",
      "isomorphic-ws" -> "4.0.1",
      "axios"         -> "0.18.0"
    ),
    jsEnv in Test := new org.scalajs.jsenv.nodejs.NodeJSEnv()
  )
  .settings(
    name := "jbok-network",
    libraryDependencies ++= http4s ++ Seq(
      "com.spinoco"              %% "fs2-http"   % "0.4.0",
      "com.spinoco"              %% "fs2-crypto" % "0.4.0",
      "com.offbynull.portmapper" % "portmapper"  % "2.0.5",
      "org.bitlet"               % "weupnp"      % "0.1.4",
    )
  )
  .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin))
  .dependsOn(common % CompileAndTest, macros, crypto)

lazy val persistent = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-persistent",
    libraryDependencies ++= Seq(
      "org.iq80.leveldb"          % "leveldb"                 % "0.10",
      "org.fusesource.leveldbjni" % "leveldbjni-all"          % "1.8",
      "com.github.cb372"          %%% "scalacache-core"       % V.scalacache,
      "com.github.cb372"          %% "scalacache-cats-effect" % V.scalacache,
      "com.github.cb372"          %% "scalacache-caffeine"    % V.scalacache,
      "io.lettuce"                % "lettuce-core"            % "5.1.3.RELEASE",
      "org.tpolecat"              %% "doobie-core"            % "0.6.0",
      "org.xerial"                % "sqlite-jdbc"             % "3.25.2"
    )
  )
  .dependsOn(common % CompileAndTest, codec)

lazy val benchmark = project
  .settings(commonSettings, noPublishSettings)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "jbok-benchmark",
    sourceDirectory in Jmh := (sourceDirectory in Test).value,
    classDirectory in Jmh := (classDirectory in Test).value,
    dependencyClasspath in Jmh := (dependencyClasspath in Test).value,
    // rewire tasks, so that 'jmh:run' automatically invokes 'jmh:compile' (otherwise a clean 'jmh:run' would fail)
    compile in Jmh := (compile in Jmh).dependsOn(compile in Test).value,
    run in Jmh := (run in Jmh).dependsOn(Keys.compile in Jmh).evaluated,
  )
  .dependsOn(core.jvm % CompileAndTest, persistent.jvm)

lazy val docs = project
  .settings(commonSettings, noPublishSettings, micrositeSettings)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(TutPlugin)
  .dependsOn(core.jvm)

// dependencies
lazy val tsec = Seq(
  "io.github.jmcardon" %% "tsec-common",
  "io.github.jmcardon" %% "tsec-hash-jca",
  "io.github.jmcardon" %% "tsec-signatures",
  "io.github.jmcardon" %% "tsec-cipher-jca",
  "io.github.jmcardon" %% "tsec-password"
).map(_ % V.tsec)

lazy val http4s = Seq(
  "org.http4s" %% "http4s-core",
  "org.http4s" %% "http4s-blaze-server",
  "org.http4s" %% "http4s-blaze-client",
  "org.http4s" %% "http4s-circe",
  "org.http4s" %% "http4s-dsl",
  "org.http4s" %% "http4s-dropwizard-metrics",
  "org.http4s" %% "http4s-prometheus-metrics"
).map(_ % V.http4s)

lazy val dropwizard = Seq(
  "io.dropwizard.metrics" % "metrics-core",
  "io.dropwizard.metrics" % "metrics-json",
  "io.dropwizard.metrics" % "metrics-jmx",
).map(_ % V.dropwizard)

lazy val prometheus = Seq(
  "io.prometheus" % "simpleclient",
  "io.prometheus" % "simpleclient_common",
  "io.prometheus" % "simpleclient_hotspot"
).map(_ % V.prometheus)

lazy val commonSettings = Seq(
  addCompilerPlugin("org.scalamacros" % "paradise"            % "2.1.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.2.4"),
  addCompilerPlugin("org.spire-math"  %% "kind-projector"     % "0.9.7"),
  //  addCompilerPlugin("ch.epfl.scala"   %% "scalac-profiling"   % "1.0.0"),
  //  addCompilerPlugin(scalafixSemanticdb),
  wartremoverErrors in (Compile, compile) := jbokWarts,
  connectInput in run := true,
  fork in Test := true,
  fork in run := true,
  parallelExecution in test := false,
  scalacOpts
)

lazy val commonJsSettings = Seq(
  fork := false,
  fork in Test := false,
  scalaJSUseMainModuleInitializer := true,
  scalaJSUseMainModuleInitializer in Test := false,
  webpackBundlingMode := BundlingMode.LibraryOnly(),
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  libraryDependencies ++= Seq(
    "org.scala-js"             %%% "scalajs-dom"   % "0.9.6",
    "com.thoughtworks.binding" %%% "dom"           % "11.0.1",
    "com.thoughtworks.binding" %%% "futurebinding" % "11.0.1"
  ),
  coverageEnabled := false, // workaround
  scalacOptions += "-P:scalajs:sjsDefinedByDefault"
)

lazy val CompileAndTest = "compile->compile;test->test"

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
  //  "-P:scalac-profiling:generate-macro-flamegraph",
  //  "-P:scalac-profiling:no-profiledb"
  //  "-Yrangepos", // required by SemanticDB compiler plugin
  //  "-Ywarn-unused-import" // required by `RemoveUnused` rule
)

lazy val micrositeSettings = Seq(
  libraryDependencies += "com.47deg" %% "github4s" % "0.18.6",
  micrositeName := "jbok",
  micrositeBaseUrl := "/jbok",
  micrositeDescription := "Just a Bunch Of Keys",
  micrositeAuthor := "siyan",
  micrositeGithubOwner := "c-block",
  micrositeGithubRepo := "jbok",
  micrositeDocumentationUrl := "https://c-block.github.io/jbok",
  micrositeFooterText := None,
  micrositeHighlightTheme := "atom-one-light",
  micrositePalette := Map(
    "brand-primary"   -> "#3e5b95",
    "brand-secondary" -> "#294066",
    "brand-tertiary"  -> "#2d5799",
    "gray-dark"       -> "#49494B",
    "gray"            -> "#7B7B7E",
    "gray-light"      -> "#E5E5E6",
    "gray-lighter"    -> "#F4F3F4",
    "white-color"     -> "#FFFFFF"
  ),
  fork in tut := true,
  scalacOptions in Tut --= Seq(
    "-Xfatal-warnings",
    "-Ywarn-unused-import",
    "-Ywarn-numeric-widen",
    "-Ywarn-dead-code",
    "-Ywarn-unused:imports",
    "-Xlint:-missing-interpolator,_"
  ),
  micrositePushSiteWith := GitHub4s,
  micrositeGithubToken := sys.env.get("GITHUB_TOKEN")
)

lazy val publishSettings = Seq(
  homepage := Some(url("https://github.com/c-block/jbok")),
  licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
  scmInfo := Some(ScmInfo(url("https://github.com/c-block/jbok"), "scm:git:git@github.com:c-block/jbok.git")),
  autoAPIMappings := true,
  apiURL := None
)

lazy val noPublishSettings = {
  import com.typesafe.sbt.pgp.PgpKeys.publishSigned
  Seq(
    skip in publish := true,
    publish := {},
    publishLocal := {},
    publishSigned := {},
    publishArtifact := false,
    publishTo := None
  )
}

lazy val releaseSettings = {
  import ReleaseTransformations._
  Seq(
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      // For non cross-build projects, use releaseStepCommand("publishSigned")
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    ),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= (
      for {
        username <- Option(System.getenv().get("SONATYPE_USERNAME"))
        password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
      } yield
        Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          username,
          password
        )
    ).toSeq,
    publishArtifact in Test := false,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/c-block/jbok"),
        "git@github.com/c-block/jbok.git"
      )
    ),
    homepage := Some(url("https://github.com/c-block/jbok")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    publishMavenStyle := true,
    pomIncludeRepository := { _ =>
      false
    },
    pomExtra := {
      <developers>
        {for ((username, name) <- contributors) yield
        <developer>
          <id>{username}</id>
          <name>{name}</name>
          <url>http://github.com/{username}</url>
        </developer>
        }
      </developers>
    }
  )
}
