import sbt.Keys._
import sbt._

object Settings {
  val projectOrg         = "org.jbok"
  val projectName        = "jbok"
  val projectDescription = "Just a Bunch Of Keys"
  val projectAuthors     = s"${projectName} authors"
  val projectLicense     = ("MIT", url("http://opensource.org/licenses/MIT"))
  val projectGithubOwner = "c-block"
  val projectGithubRepo  = "jbok"

  lazy val common = compilerPlugins ++ WartRemoverPlugin.settings ++ Seq(
    cancelable in Global := true,
    organization := projectOrg,
    name := projectName,
    description := projectDescription,
    version := Versions.version,
    licenses += projectLicense,
    scalaVersion := Versions.scala212Version,
    test / parallelExecution := false,
    scalacOptions --= Seq(
      "-Xfatal-warnings", // some false-positives
      "-Ywarn-numeric-widen"
    )
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
