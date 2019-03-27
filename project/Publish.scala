import com.typesafe.sbt.pgp.PgpKeys.publishSigned
import com.typesafe.sbt.pgp.PgpSettings._
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport._
import ReleaseTransformations._

object Publish {
  lazy val contributors = Map(
    "blazingsiyan" -> "siyan"
  )

  lazy val settings = generalSettings ++ sharedSettings ++ credentialSettings ++ pgpSettings ++ releaseSettings

  lazy val releaseSettings = Seq(
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
  )

  lazy val noPublishSettings = Seq(
    skip in publish := true,
    publish := {},
    publishLocal := {},
    publishSigned := {},
    publishArtifact := false,
    publishTo := None
  )

  private lazy val credentialSettings = Seq(
    credentials ++= (for {
      username <- Option(System.getenv().get("SONATYPE_USERNAME"))
      password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
    } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
  )

  private lazy val pgpSettings =
    Option(System.getenv().get("PGP_PASSPHRASE"))
      .map(s => pgpPassphrase := Some(s.toCharArray))
      .toSeq

  private lazy val sharedSettings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := Function.const(false),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("Snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("Releases" at nexus + "service/local/staging/deploy/maven2")
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

  private lazy val generalSettings = Seq(
    homepage := Some(url("https://github.com/c-block/jbok")),
    licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    scmInfo := Some(ScmInfo(url("https://github.com/c-block/jbok"), "scm:git:git@github.com:c-block/jbok.git")),
    autoAPIMappings := true,
    apiURL := None
  )
}
