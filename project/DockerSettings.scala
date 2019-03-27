import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.Keys.packageName

object DockerSettings {
  val settings = Seq(
    packageName in Docker := "jbok",
    dockerBaseImage := "openjdk:8-jre-alpine"
  )
}
