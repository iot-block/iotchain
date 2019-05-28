import sbt.Keys._
import sbt._

object Settings {
  lazy val common = compilerPlugins ++ WartRemoverPlugin.settings ++ Seq(
    cancelable in Global := true,
    organization := "org.jbok",
    name := "jbok",
    description := "Just a Bunch Of Keys",
    version := Versions.version,
    licenses ++= Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
    scalaVersion := Versions.scala212Version,
    test / parallelExecution := false,
    scalacOptions ++= ScalacSettings.base ++ ScalacSettings.specificFor(scalaVersion.value) ++ ScalacSettings.strictBase ++ ScalacSettings.strictSpecificFor(scalaVersion.value),
    scalacOptions in Test --= (ScalacSettings.strictBase ++ ScalacSettings.strictSpecificFor(scalaVersion.value)),
    javacOptions ++= JavacSettings.base ++ JavacSettings.specificFor(scalaVersion.value),
  )

  lazy val jvmCommon = Seq(
    fork := true,
    run / connectInput := true
  )

  private lazy val compilerPlugins = Seq(
    addCompilerPlugin("org.scalamacros" % "paradise"            % "2.1.0" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.3.0"),
    addCompilerPlugin("org.typelevel"   %% "kind-projector"     % "0.10.1")
  )

  private object ScalacSettings {
    val base = Seq(
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-encoding",
      "UTF-8", // Specify character encoding used by source files.
      "-explaintypes", // Explain type errors in more detail.
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
      "-language:higherKinds", // Allow higher-kinded types
      "-language:implicitConversions", // Allow definition of implicit functions called views
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      "-Ypartial-unification", // Enable partial unification in type constructor inference
      "-language:postfixOps"
    )

    def specificFor(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) => Seq("-target:jvm-1.8")
      case Some((2, 11)) => Seq("-target:jvm-1.8", "-optimise")
      case Some((2, 10)) => Seq("-target:jvm-1.7", "-optimise")
      case _             => Nil
    }

    val strictBase = Seq(
      "-Xlint",
      "-Ywarn-dead-code", // Warn when dead code is identified.
      "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
      "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
      "-Ywarn-inaccessible",
      "-Ywarn-nullary-override"
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
