import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._
import scalajsbundler.util.JSON

object ScalaJS {
  val common = Seq(
    fork := false,
    fork in test := false,
    scalaJSUseMainModuleInitializer := false,
    scalaJSUseMainModuleInitializer in test := false,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
    scalacOptions -= "-Ywarn-unused:params"
  )

  val webpackSettings = Seq(
    useYarn := true,
    additionalNpmConfig in Compile := Map(
      "license"     -> JSON.str("MIT"),
      "name"        -> JSON.str("JBOK"),
      "description" -> JSON.str("JBOK"),
      "version"     -> JSON.str("0.0.1"),
      "author"      -> JSON.str("JBOK authors"),
      "repository" -> JSON.obj(
        "type" -> JSON.str("git"),
        "url"  -> JSON.str("https://github.com/c-block/jbok.git")
      ),
      "build" -> JSON.obj(
        "appId" -> JSON.str("org.jbok.app")
      ),
      "scripts" -> JSON.obj(
        "pack" -> JSON.str("electron-builder --dir"),
        "dist" -> JSON.str("electron-builder")
      )
    ),
    npmDevDependencies in Compile ++= Seq(
      "file-loader"         -> "1.1.11",
      "style-loader"        -> "0.20.3",
      "css-loader"          -> "0.28.11",
      "html-webpack-plugin" -> "3.2.0",
      "copy-webpack-plugin" -> "4.5.1",
      "webpack-merge"       -> "4.1.2",
      "electron-builder"    -> "20.28.4"
    ),
    version in webpack := "4.8.1",
    version in startWebpackDevServer := "3.1.4",
    webpackConfigFile := Some((resourceDirectory in Compile).value / "webpack.config.js"),
    webpackBundlingMode := BundlingMode.LibraryAndApplication()
  )
}
