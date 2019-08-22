import com.typesafe.sbt.packager.Keys.packageName
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._

object DockerSettings {
  val settings = Seq(
    packageName in Docker := "iotchain/"+Settings.projectName,
    version in Docker := "v1.0.3.release",
    dockerCommands := Seq(
      Cmd("FROM", "openjdk:8-jre"),
      Cmd("RUN", "groupadd -r iotchain && useradd -r -g iotchain iotchain"),
      Cmd(
        "RUN",
        s"""rm -rf /var/lib/iotchain /var/log/iotchain && \\
           |mkdir -p /var/lib/iotchain /var/log/iotchain && \\
           |chown -R iotchain:iotchain /var/lib/iotchain /var/log/iotchain && \\
           |chmod 777 /var/lib/iotchain /var/log/iotchain
         """.stripMargin
      ),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("ADD", "--chown=iotchain:iotchain opt /opt"),
      Cmd("EXPOSE", "30314 30315"),
      Cmd("VOLUME", "/var/lib/iotchain, /var/log/iotchain"),
      Cmd("ENTRYPOINT", "/opt/docker/bin/app-main")
    )
  )
}
