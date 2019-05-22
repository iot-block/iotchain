package jbok.common.log

import java.nio.file.{Path, Paths}

import cats.effect.Sync
import scribe.handler.LogHandler
import scribe.writer.FileWriter
import scribe.writer.file.LogPath
import cats.implicits._

object LoggerPlatform {
  def initConfig[F[_]: Sync](config: LogConfig): F[Unit] = {
    val level = Level.fromName(config.level)
    Logger.setRootLevel(level) >>
      (config.logDir match {
        case "/dev/null" =>
          Logger.setRootHandlers(Logger.consoleHandler(level.some))
        case dir =>
          Logger.setRootHandlers(
            Logger.consoleHandler(level.some),
            fileHandler(Paths.get(dir), level.some)
          )
      })
  }

  def fileHandler(directory: Path, minimumLevel: Option[Level] = None): LogHandler = LogHandler(
    Logger.fileFormatter,
    FileWriter().nio
      .path(LogPath.simple("jbok.log", directory = directory))
      .rolling(LogPath.daily(prefix = "jbok", directory = directory)),
    minimumLevel.map(Logger.fromJbokLevel)
  )
}
