import sbt._
import sbt.Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object Libs {
  lazy val common
    : Seq[Setting[_]] = logging ++ dropwizard ++ prometheus ++ circe ++ scodec ++ graph ++ test ++ (libraryDependencies ++= Seq(
    // typelevel
    "org.typelevel" %%% "cats-effect"          % Versions.catsEffect,
    "org.typelevel" %% "cats-collections-core" % Versions.catsCollections,
    "co.fs2"        %%% "fs2-core"             % Versions.fs2,
    "co.fs2"        %% "fs2-io"                % Versions.fs2,
    "org.typelevel" %%% "spire"                % "0.16.0",
    // enum
    "com.beachape" %%% "enumeratum"       % "1.5.13",
    "com.beachape" %%% "enumeratum-circe" % "1.5.13",
    // files
    "com.github.pathikrit" %% "better-files" % "3.5.0",
    // macro
    "com.propensive" %%% "magnolia" % "0.10.0",
    // scalajs-stubs
    "org.scala-js" %% "scalajs-stubs" % "0.6.26"
  ))

  lazy val graph = libraryDependencies ++= Seq(
    "org.scala-graph" %%% "graph-core" % "1.12.5",
    "org.scala-graph" %% "graph-dot"   % "1.12.1"
  )

  lazy val circe = libraryDependencies ++= Seq(
    "io.circe" %%% "circe-core"       % Versions.circe,
    "io.circe" %%% "circe-generic"    % Versions.circe,
    "io.circe" %%% "circe-parser"     % Versions.circe,
    "io.circe" %%% "circe-derivation" % "0.9.0-M5"
  )

  lazy val scodec = libraryDependencies ++= Seq(
    "org.scodec" %%% "scodec-bits"  % "1.1.6",
    "org.scodec" %%% "scodec-core"  % "1.10.3",
    "org.scodec" %% "scodec-stream" % "1.2.0"
  )

  lazy val test = libraryDependencies ++= Seq(
    "org.scalatest"  %%% "scalatest"  % "3.0.5",
    "org.scalacheck" %%% "scalacheck" % "1.13.4"
  ).map(_ % Test)

  lazy val logging = libraryDependencies ++= Seq(
    "com.outr" %%% "scribe"      % Versions.scribe,
    "com.outr" %% "scribe-slf4j" % Versions.scribe
  )

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
    "org.http4s" %% "http4s-dropwizard-metrics",
    "org.http4s" %% "http4s-prometheus-metrics"
  ).map(_        % Versions.http4s) ++ Seq(
    "org.http4s" %% "rho-swagger" % "0.19.0-M6"
  )

  lazy val dropwizard = libraryDependencies ++= Seq(
    "io.dropwizard.metrics" % "metrics-core",
    "io.dropwizard.metrics" % "metrics-json",
    "io.dropwizard.metrics" % "metrics-jmx",
  ).map(_ % Versions.dropwizard)

  lazy val prometheus = libraryDependencies ++= Seq(
    "io.prometheus" % "simpleclient",
    "io.prometheus" % "simpleclient_common",
    "io.prometheus" % "simpleclient_hotspot"
  ).map(_ % Versions.prometheus)

  private val doobie = Seq(
    "org.tpolecat" %% "doobie-core",
    "org.tpolecat" %% "doobie-hikari"
  ).map(_ % Versions.doobie)

  private val quill = Seq(
    "io.getquill" %% "quill-sql",
    "io.getquill" %% "quill-core",
    "io.getquill" %% "quill-jdbc",
    "io.getquill" %% "quill-jdbc-monix",
    "io.getquill" %% "quill-async"
  ).map(_ % Versions.quill)

  lazy val persistent = scalacache ++ (libraryDependencies ++= drivers ++ quill ++ doobie)

  lazy val drivers = Seq(
    "org.iq80.leveldb"          % "leveldb"        % "0.10",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
    "org.rocksdb"               % "rocksdbjni"     % "5.17.2",
    "io.lettuce"                % "lettuce-core"   % "5.1.3.RELEASE",
    "org.xerial"                % "sqlite-jdbc"    % "3.25.2",
    "org.flywaydb"              % "flyway-core"    % "5.0.5",
  )

  lazy val scalacache = libraryDependencies ++= Seq(
    "com.github.cb372" %%% "scalacache-core"       % Versions.scalacache,
    "com.github.cb372" %% "scalacache-cats-effect" % Versions.scalacache,
    "com.github.cb372" %% "scalacache-caffeine"    % Versions.scalacache
  )

  lazy val network = libraryDependencies ++=
    Seq(
      "com.spinoco"              %% "fs2-http"   % "0.4.0",
      "com.spinoco"              %% "fs2-crypto" % "0.4.0",
      "com.offbynull.portmapper" % "portmapper"  % "2.0.5",
      "org.bitlet"               % "weupnp"      % "0.1.4"
    )

  lazy val fastparse = libraryDependencies += "com.lihaoyi" %%% "fastparse" % "2.1.0"

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
      "ws"            -> "6.1.2",
      "isomorphic-ws" -> "4.0.1",
      "axios"         -> "0.18.0"
    )
  }
}
