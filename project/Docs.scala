import _root_.io.github.davidgregory084.TpolecatPlugin.autoImport.filterConsoleScalacOptions
import microsites.MicrositesPlugin.autoImport._
import sbt.Keys._
import sbt._
import mdoc.MdocPlugin.autoImport._

object Docs {
  lazy val settings = micrositeSettings ++ mdocSettings

  private lazy val micrositeSettings = Seq(
    libraryDependencies += "com.47deg" %% "github4s" % "0.18.6",
    micrositeName := "JBOK",
    micrositeBaseUrl := "/jbok",
    micrositeDescription := "Just a Bunch Of Keys",
    micrositeAuthor := "JBOK contributors",
    micrositeGithubOwner := "c-block",
    micrositeGithubRepo := "jbok",
    micrositeDocumentationUrl := "https://c-block.github.io/jbok",
    micrositeFooterText := None,
    micrositeHighlightTheme := "atom-one-light",
    micrositePalette := Map(
      "brand-primary"   -> "#3e5b95",
      "brand-secondary" -> "#294066",
      "brand-tertiary"  -> "#2d5799",
      "gray-dark"       -> "#49494B",
      "gray"            -> "#7B7B7E",
      "gray-light"      -> "#E5E5E6",
      "gray-lighter"    -> "#F4F3F4",
      "white-color"     -> "#FFFFFF"
    ),
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
    micrositeCompilingDocsTool := WithMdoc,
    mdocIn := sourceDirectory.value / "main" / "tut"
  )

  private lazy val mdocSettings = Seq(
    fork in mdoc := true,
    scalacOptions in mdoc ~= filterConsoleScalacOptions
  )
}
