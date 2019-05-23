package jbok.app

import scala.concurrent.duration._

final case class AppConfig(interface: String, port: Int, refreshTime: FiniteDuration, blockHistorySize: Int, clientTimeout: FiniteDuration, clientRetry: Int) {
  val url = s"http://${interface}:${port}"
}
object AppConfig {
  val default = AppConfig(
    "127.0.0.1",
    30315,
    5.seconds,
    100,
    15.seconds,
    3
  )
}
