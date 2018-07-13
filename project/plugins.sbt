addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.2")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.23")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.5.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.13.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.5")

// fix https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"
