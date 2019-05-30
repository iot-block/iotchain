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
    scalacOptions -= "-Xfatal-warnings"
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
}
