import sbt._
import sbt.Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object Libs {
  lazy val common: Seq[Setting[_]] = di ++ logging ++ prometheus ++ circe ++ scodec ++ graph ++ test ++ typelevel ++ enumeratum ++ monocle ++ (libraryDependencies ++= Seq(
    "com.github.pathikrit" %% "better-files"  % "3.5.0",
    "com.propensive"       %%% "magnolia"     % "0.10.0",
    "org.scala-js"         %% "scalajs-stubs" % "0.6.26",
  ))

  lazy val terminal = libraryDependencies ++= Seq(
    "net.team2xh" %% "onions"  % "1.0.1",
    "net.team2xh" %% "scurses" % "1.0.1"
  )

  lazy val test = libraryDependencies ++= Seq(
    "org.scalatest"  %%% "scalatest"  % "3.0.5",
    "org.scalacheck" %%% "scalacheck" % "1.13.4"
  ).map(_ % Test)

  lazy val sql = doobie ++ (libraryDependencies ++= Seq(
    "org.xerial"   % "sqlite-jdbc"          % "3.25.2",
    "org.flywaydb" % "flyway-core"          % "5.0.5",
    "mysql"        % "mysql-connector-java" % "8.0.15"
  ))

  lazy val kv = libraryDependencies ++= Seq("org.rocksdb" % "rocksdbjni" % "6.0.1")

  lazy val network = libraryDependencies ++=
    Seq(
      "com.spinoco"              %% "fs2-crypto" % "0.4.0",
      "com.offbynull.portmapper" % "portmapper"  % "2.0.5",
      "org.bitlet"               % "weupnp"      % "0.1.4"
    )

  lazy val fastparse = libraryDependencies += "com.lihaoyi" %%% "fastparse" % "2.1.0"

  lazy val tsec = libraryDependencies ++= Seq(
    "io.github.jmcardon" %% "tsec-common",
    "io.github.jmcardon" %% "tsec-hash-jca",
    "io.github.jmcardon" %% "tsec-cipher-jca",
    "io.github.jmcardon" %% "tsec-signatures",
    "io.github.jmcardon" %% "tsec-password",
    "io.github.jmcardon" %% "tsec-jwt-mac",
    "io.github.jmcardon" %% "tsec-jwt-sig",
    "io.github.jmcardon" %% "tsec-http4s",
  ).map(_ % Versions.tsec)

  lazy val http4s = libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-core",
    "org.http4s" %% "http4s-blaze-server",
    "org.http4s" %% "http4s-blaze-client",
    "org.http4s" %% "http4s-circe",
    "org.http4s" %% "http4s-dsl",
    "org.http4s" %% "http4s-prometheus-metrics",
    "org.http4s" %% "http4s-okhttp-client"
  ).map(_ % Versions.http4s)

  private lazy val typelevel = libraryDependencies ++= Seq(
    "org.typelevel"    %%% "cats-effect"            % Versions.catsEffect,
    "co.fs2"           %%% "fs2-core"               % Versions.fs2,
    "co.fs2"           %% "fs2-io"                  % Versions.fs2,
    "com.github.cb372" %%% "cats-retry-cats-effect" % "0.2.5"
  )

  private lazy val enumeratum = libraryDependencies ++= Seq(
    "com.beachape" %% "enumeratum"            % Versions.enumeratum,
    "com.beachape" %% "enumeratum-cats"       % "1.5.15",
    "com.beachape" %% "enumeratum-scalacheck" % Versions.enumeratum,
  )

  private lazy val monocle = libraryDependencies ++= Seq(
    "com.github.julien-truffaut" %% "monocle-core"  % Versions.monocle,
    "com.github.julien-truffaut" %% "monocle-macro" % Versions.monocle,
  )

  private lazy val graph = libraryDependencies ++= Seq(
    "org.scala-graph" %%% "graph-core" % "1.12.5",
    "org.scala-graph" %% "graph-dot"   % "1.12.1"
  )

  private lazy val circe = libraryDependencies ++= Seq(
    "io.circe" %%% "circe-core"           % Versions.circe,
    "io.circe" %%% "circe-parser"         % Versions.circe,
    "io.circe" %%% "circe-generic"        % Versions.circe,
    "io.circe" %%% "circe-generic-extras" % Versions.circe,
    "io.circe" %% "circe-yaml"            % "0.10.0",
  )

  private lazy val scodec = libraryDependencies ++= Seq(
    "org.scodec" %%% "scodec-bits"  % "1.1.10",
    "org.scodec" %%% "scodec-core"  % "1.11.3",
    "org.scodec" %% "scodec-stream" % "1.2.1"
  )

  private lazy val di = libraryDependencies ++= Seq(
    "com.github.pshirshov.izumi.r2" %% "distage-core"    % Versions.izumi,
    "com.github.pshirshov.izumi.r2" %% "distage-cats"    % Versions.izumi,
    "com.github.pshirshov.izumi.r2" %% "distage-static"  % Versions.izumi,
    "com.github.pshirshov.izumi.r2" %% "distage-plugins" % Versions.izumi
  )

  private lazy val logging = libraryDependencies ++= Seq(
    "com.outr" %%% "scribe"      % Versions.scribe,
    "com.outr" %% "scribe-slf4j" % Versions.scribe
  )

  private lazy val prometheus = libraryDependencies ++= Seq(
    "io.prometheus" % "simpleclient",
    "io.prometheus" % "simpleclient_common",
    "io.prometheus" % "simpleclient_hotspot"
  ).map(_ % Versions.prometheus)

  private lazy val doobie = libraryDependencies ++= Seq(
    "org.tpolecat" %% "doobie-core",
    "org.tpolecat" %% "doobie-hikari",
    "org.tpolecat" %% "doobie-h2"
  ).map(_ % Versions.doobie)

  object js {
    val common = libraryDependencies ++= Seq(
      "org.scala-js"             %%% "scalajs-dom"   % "0.9.6",
      "com.thoughtworks.binding" %%% "dom"           % "11.0.1",
      "com.thoughtworks.binding" %%% "futurebinding" % "11.0.1"
    )

    val crypto = npmDependencies in Compile ++= Seq(
      "elliptic"  -> "6.4.0",
      "crypto-js" -> "3.1.9-1"
    )

    val network = npmDependencies in Compile ++= Seq(
      "axios" -> "0.18.0"
    )
  }
}
