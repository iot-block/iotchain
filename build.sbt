import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

organization in ThisBuild := "org.jbok"

name := "jbok"

description := "Just a Bunch Of Keys"

scalaVersion in ThisBuild := "2.12.6"

cancelable in Global := true

lazy val contributors = Map(
  "blazingsiyan" -> "siyan"
)

lazy val V = new {
  val circe  = "0.9.1"
  val tsec   = "0.0.1-M11"
  val http4s = "0.18.12"
  val fs2    = "0.10.4"
}

lazy val logging = Seq(
  "ch.qos.logback"             % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging"  % "3.8.0",
  "org.log4s"                  %% "log4s"          % "1.6.1"
)

lazy val tsec = Seq(
  "io.github.jmcardon" %% "tsec-common"        % V.tsec,
  "io.github.jmcardon" %% "tsec-hash-jca"      % V.tsec,
  "io.github.jmcardon" %% "tsec-hash-bouncy"   % V.tsec,
  "io.github.jmcardon" %% "tsec-signatures"    % V.tsec,
  "io.github.jmcardon" %% "tsec-cipher-jca"    % V.tsec,
  "io.github.jmcardon" %% "tsec-cipher-bouncy" % V.tsec,
  "io.github.jmcardon" %% "tsec-password"      % V.tsec
)

lazy val http4s = Seq(
  "org.http4s" %% "http4s-core",
  "org.http4s" %% "http4s-blaze-server",
  "org.http4s" %% "http4s-blaze-client",
  "org.http4s" %% "http4s-circe",
  "org.http4s" %% "http4s-dsl"
).map(_ % V.http4s)

lazy val commonSettings = Seq(
  addCompilerPlugin("org.scalamacros" % "paradise"            % "2.1.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.2.4"),
  addCompilerPlugin("org.spire-math"  %% "kind-projector"     % "0.9.7"),
  fork in test := false,
  fork in run := true,
  parallelExecution in test := false,
  scalacOpts
)

lazy val jbok = project
  .in(file("."))
  .aggregate(coreJVM, appJVM)
  .settings(noPublishSettings)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-common",
    libraryDependencies ++= logging ++ Seq(
      "org.typelevel"         %%% "cats-core"        % "1.1.0",
      "org.typelevel"         %%% "cats-effect"      % "1.0.0-RC",
      "io.circe"              %%% "circe-core"       % V.circe,
      "io.circe"              %%% "circe-generic"    % V.circe,
      "io.circe"              %%% "circe-parser"     % V.circe,
      "org.scala-graph"       %%% "graph-core"       % "1.12.5",
      "org.scala-graph"       %% "graph-dot"         % "1.12.1",
      "com.github.mpilquist"  %%% "simulacrum"       % "0.12.0",
      "com.beachape"          %%% "enumeratum"       % "1.5.13",
      "com.beachape"          %%% "enumeratum-circe" % "1.5.13",
      "co.fs2"                %%% "fs2-core"         % V.fs2,
      "org.scodec"            %%% "scodec-bits"      % "1.1.5",
      "org.scodec"            %%% "scodec-core"      % "1.10.3",
      "org.scodec"            %% "scodec-stream"     % "1.1.0",
      "com.github.pureconfig" %% "pureconfig"        % "0.9.1",
      "org.scalatest"         %%% "scalatest"        % "3.0.5" % Test,
      "org.scalacheck"        %%% "scalacheck"       % "1.13.4" % Test
    )
  )

lazy val commonJS  = common.js
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
  .dependsOn(common % CompileAndTest, codec, crypto, p2p, persistent)

lazy val coreJS  = core.js
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
      "org.scorexfoundation" %% "scrypto"       % "2.0.5",
      "org.bouncycastle"     % "bcprov-jdk15on" % "1.59",
      "net.i2p.crypto"       % "eddsa"          % "0.3.0"
    )
  )
  .dependsOn(common % CompileAndTest, codec, persistent)

lazy val cryptoJS  = crypto.js.enablePlugins(ScalaJSBundlerPlugin)
lazy val cryptoJVM = crypto.jvm

lazy val p2p = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-p2p"
  )
  .dependsOn(common % CompileAndTest, network, persistent, codec)

lazy val p2pJS  = p2p.js
lazy val p2pJVM = p2p.jvm

lazy val codec = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-codec"
  )
  .dependsOn(common % CompileAndTest)

lazy val codecJS  = codec.js
lazy val codecJVM = codec.jvm

lazy val examples = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-examples"
  )
  .dependsOn(core % CompileAndTest)

lazy val examplesJS  = examples.js
lazy val examplesJVM = examples.jvm

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
      "com.thoughtworks.binding" %%% "binding"   % "11.0.1",
      "com.lihaoyi"              %%% "upickle"   % "0.6.6",
      "com.lihaoyi"              %%% "scalatags" % "0.6.7",
      "com.monovore"             %% "decline"    % "0.4.0-RC1"
    )
  )
  .dependsOn(common % CompileAndTest, core % CompileAndTest, network)

lazy val appJS  = app.js
lazy val appJVM = app.jvm

lazy val macros = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-macros"
  )
  .dependsOn(common % CompileAndTest, codec)

lazy val macrosJS  = macros.js
lazy val macrosJVM = macros.jvm

lazy val network = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .settings(
    name := "jbok-network",
    libraryDependencies ++= logging ++ Seq(
      "com.spinoco" %% "fs2-http" % "0.3.0"
    )
  )
  .jsSettings(commonJsSettings)
  .dependsOn(common % CompileAndTest, macros, crypto)

lazy val networkJS  = network.js
lazy val networkJVM = network.jvm

lazy val persistent = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .jsSettings(commonJsSettings)
  .settings(
    name := "jbok-persistent",
    libraryDependencies ++= Seq(
      "org.iq80.leveldb" % "leveldb" % "0.10",
      "io.monix"         %% "monix"  % "3.0.0-RC1"
    )
  )
  .dependsOn(common % CompileAndTest, codec)

lazy val persistentJS  = persistent.js
lazy val persistentJVM = persistent.jvm

lazy val benchmark = project
  .settings(commonSettings, noPublishSettings)
  .settings(
    name := "jbok-benchmark"
  )
  .enablePlugins(JmhPlugin)
  .dependsOn(persistentJVM)

lazy val docs = project
  .settings(commonSettings, noPublishSettings, micrositeSettings)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(TutPlugin)
  .dependsOn(coreJVM)

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
