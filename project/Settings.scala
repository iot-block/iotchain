import sbt.Keys._
import sbt._
import scoverage.ScoverageSbtPlugin.autoImport._

object Settings {
  lazy val common = compilerPlugins ++ WartRemoverPlugin.settings ++ Seq(
    cancelable in Global := true,
    organization := "org.jbok",
    name := "jbok",
    description := "Just a Bunch Of Keys",
    version := Versions.version,
    licenses ++= Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
    scalaVersion := Versions.scala212Version,
    connectInput in run := true,
    connectInput := true,
    parallelExecution in test := false,
    scalacOptions ++= ScalacSettings.base ++ ScalacSettings.specificFor(scalaVersion.value),
    javacOptions ++= JavacSettings.base ++ JavacSettings.specificFor(scalaVersion.value),
  )

  lazy val commonJs = Seq(
    resolvers += Resolver.bintrayRepo("oyvindberg", "ScalablyTyped"),
    coverageEnabled := false, // workaround
    scalacOptions += "-P:scalajs:sjsDefinedByDefault"
  )

  private lazy val compilerPlugins = Seq(
    addCompilerPlugin("org.scalamacros" % "paradise"            % "2.1.0" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.2.4"),
    addCompilerPlugin("org.spire-math"  %% "kind-projector"     % "0.9.7")
  )

  private object ScalacSettings {
    val base = Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-Ypartial-unification",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps"
    )

    def specificFor(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) => Seq("-target:jvm-1.8")
      case Some((2, 11)) => Seq("-target:jvm-1.8", "-optimise")
      case Some((2, 10)) => Seq("-target:jvm-1.7", "-optimise")
      case _             => Nil
    }

    val strictBase = Seq(
      "-Xfatal-warnings",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Ywarn-inaccessible",
      "-Ywarn-nullary-override"
    )

    val extra = Seq(
      //  "-P:scalac-profiling:generate-macro-flamegraph",
      //  "-P:scalac-profiling:no-profiledb"
      //  "-Yrangepos", // required by SemanticDB compiler plugin
      //  "-Ywarn-unused-import" // required by `RemoveUnused` rule
    )

    def strictSpecificFor(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) => Seq("-Ywarn-unused", "-Ywarn-unused-import")
      case Some((2, 11)) => Seq("-Ywarn-unused", "-Ywarn-unused-import")
      case _             => Nil
    }
  }

  private object JavacSettings {
    val base = Seq("-Xlint")

    def specificFor(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) => Seq("-source", "1.8", "-target", "1.8")
      case Some((2, 11)) => Seq("-source", "1.8", "-target", "1.8")
      case Some((2, 10)) => Seq("-source", "1.7", "-target", "1.7")
      case _             => Nil
    }
  }
}
