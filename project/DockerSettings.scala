import com.typesafe.sbt.packager.Keys.packageName
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._

object DockerSettings {
  val settings = Seq(
    packageName in Docker := "jbok",
    version in Docker := "latest",
    dockerEntrypoint := Seq("/opt/docker/bin/app-main"),
    dockerBaseImage := "openjdk:8-jre",
    dockerExposedPorts := Seq(30314, 30315)
  )
}
