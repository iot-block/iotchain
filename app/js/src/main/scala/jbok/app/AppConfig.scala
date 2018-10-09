package jbok.app
import java.net.URI

case class AppConfig(uri: URI)
object AppConfig {
  val default = AppConfig(
    new URI("ws://localhost:8888")
  )
}
