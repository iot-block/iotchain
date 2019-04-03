import com.tapad.docker.DockerComposePlugin.autoImport._
import com.typesafe.sbt.packager.Keys.packageName
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._

object DockerSettings {
  val settings = Seq(
    packageName in Docker := "jbok",
    version in Docker := "latest",
    dockerBaseImage := "openjdk:8-jre-alpine"
  )

  val composeSettings = Seq(
//    composeContainerPauseBeforeTestSeconds := 0, //Delay between containers start and test execution, seconds. Default is 0 seconds - no delay
    composeFile := (baseDirectory.value.getParentFile.getParentFile / "docker/docker-compose.yml").getCanonicalPath, // Specify the full path to the Compose File to use to create your test instance. It defaults to docker-compose.yml in your resources folder.
//    composeServiceName := "jbok", // Specify the name of the service in the Docker Compose file being tested. This setting prevents the service image from being pull down from the Docker Registry. It defaults to the sbt Project name.
//    composeServiceVersionTask := "", // The version to tag locally built images with in the docker-compose file. This defaults to the 'version' SettingKey.
    composeNoBuild := false, // True if a Docker Compose file is to be started without building any images and only using ones that already exist in the Docker Registry. This defaults to False.
    composeRemoveContainersOnShutdown := true, // True if a Docker Compose should remove containers when shutting down the compose instance. This defaults to True.
    composeRemoveNetworkOnShutdown := true, // True if a Docker Compose should remove the network it created when shutting down the compose instance. This defaults to True.
    composeContainerStartTimeoutSeconds := 500, // The amount of time in seconds to wait for the containers in a Docker Compose instance to start. Defaults to 500 seconds.
    composeRemoveTempFileOnShutdown := true,    // True if a Docker Compose should remove the post Custom Tag processed Compose File on shutdown. This defaults to True.
//      dockerMachineName := ,// If running on OSX the name of the Docker Machine Virtual machine being used. If not overridden it is set to 'default'
    dockerImageCreationTask := (publishLocal in Docker).value, // The sbt task used to create a Docker image. For sbt-docker this should be set to 'docker.value' for the sbt-native-packager this should be set to '(publishLocal in Docker).value'.
    suppressColorFormatting := false // True to suppress all color formatting in the output from the plugin. This defaults to the value of the 'sbt.log.noformat' property. If you are using `sbt-extras`, the use of the command line switch `-no-colors` will set this to True.
//      testTagsToExecute := // Set of ScalaTest Tags to execute when dockerComposeTest is run. Separate multiple tags by a comma. It defaults to executing all tests.
//      testExecutionArgs := // Additional ScalaTest Runner argument options to pass into the test runner. For example, this can be used for the generation of test reports.
//      testExecutionExtraConfigTask := // An sbt task that returns a Map[String,String] of variables to pass into the ScalaTest Runner ConfigMap (in addition to standard service/port mappings).
//      testDependenciesClasspath := // The path to all managed and unmanaged Test and Compile dependencies. This path needs to include the ScalaTest Jar for the tests to execute. This defaults to all managedClasspath and unmanagedClasspath in the Test and fullClasspath in the Compile Scope.
//      testCasesJar := // The path to the Jar file containing the tests to execute. This defaults to the Jar file with the tests from the current sbt project.
//      testCasesPackageTask := // The sbt Task to package the test cases used when running 'dockerComposeTest'. This defaults to the 'packageBin' task in the 'Test' Scope.
//      variablesForSubstitution := // A Map[String,String] of variables to substitute in your docker-compose file. These are substituted substituted by the plugin and not using environment variables.
//      variablesForSubstitutionTask := // An sbt task that returns a Map[String,String] of variables to substitute in your docker-compose file. These are substituted by the plugin and not using environment variables.
  )
}
