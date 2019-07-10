import com.typesafe.sbt.packager.Keys.packageName
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._

object DockerSettings {
  val settings = Seq(
    packageName in Docker := "iotchain/"+Settings.projectName,
    version in Docker := "0.0.1beta",
    dockerCommands := Seq(
      Cmd("FROM", "openjdk:8-jre"),
      Cmd("RUN", "groupadd -r jbok && useradd -r -g jbok jbok"),
      Cmd(
        "RUN",
        s"""rm -rf /var/lib/jbok /var/log/jbok && \\
           |mkdir -p /var/lib/jbok /var/log/jbok && \\
           |chown -R jbok:jbok /var/lib/jbok /var/log/jbok && \\
           |chmod 777 /var/lib/jbok /var/log/jbok
         """.stripMargin
      ),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("ADD", "--chown=jbok:jbok opt /opt"),
      Cmd("EXPOSE", "30314 30315"),
      Cmd("VOLUME", "/var/lib/jbok, /var/log/jbok"),
      Cmd("ENTRYPOINT", "/opt/docker/bin/app-main")
    )
  )
}
