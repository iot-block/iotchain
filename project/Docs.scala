import sbt._
import sbt.Keys._
import microsites.MicrositesPlugin.autoImport._
import tut.TutPlugin.autoImport._

object Docs {
  lazy val settings = micrositeSettings ++ tutSettings

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
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN")
  )

  private lazy val tutSettings = Seq(
    fork in tut := true,
    scalacOptions in Tut --= Seq(
      "-Xfatal-warnings",
      "-Ywarn-unused-import",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-unused:imports",
      "-Xlint:-missing-interpolator,_"
    )
  )
}
