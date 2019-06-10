package jbok.common.log

import java.nio.file.{Path, Paths}

import cats.effect.Sync
import cats.implicits._
import jbok.common.FileUtil
import scribe.handler.LogHandler
import scribe.writer.FileWriter
import scribe.writer.file.LogPath

import scala.concurrent.duration._

object LoggerPlatform {
  def initConfig[F[_]: Sync](config: LogConfig): F[Unit] = {
    val level = Level.fromName(config.level)
    Logger.setRootLevel(level) >>
      (config.logDir match {
        case "/dev/null" =>
          Logger.setRootHandlers(Logger.consoleHandler(level.some))
        case dir =>
          FileUtil[F].open(Paths.get(config.logDir), create = true, asDirectory = true) >>
            Logger.setRootHandlers(
              Logger.consoleHandler(level.some),
              fileHandler(Paths.get(dir), level.some, config.maxLogs)
            )
      })
  }

  def fileHandler(directory: Path, minimumLevel: Option[Level] = None, maxLogs: Int = 15): LogHandler = LogHandler(
    Logger.fileFormatter,
    FileWriter().nio
      .path(LogPath.simple("jbok.log", directory = directory))
      .rolling(LogPath.daily(prefix = "jbok", directory = directory))
      .maxLogs(maxLogs, checkRate = 1.seconds),
    minimumLevel.map(Logger.fromJbokLevel)
  )
}
