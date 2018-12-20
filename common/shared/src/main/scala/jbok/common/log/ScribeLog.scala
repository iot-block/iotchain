package jbok.common.log

import _root_.scribe.{Logger, Level => SL}
import cats.effect.Sync
import scribe.format.Formatter
import scribe.handler.LogHandler
import scribe.output.{Color, ColoredOutput}

object ScribeLog {
  def getLogger(name: String) = Logger(name)

  def consoleHandler(minimumLevel: Option[Level] = None): LogHandler = LogHandler(
    consoleFormatter,
    minimumLevel = minimumLevel.map(fromJbokLevel)
  )

  def setLevel[F[_]: Sync](level: Level): F[Unit] = Sync[F].delay {
    Logger.root.withMinimumLevel(fromJbokLevel(level)).replace()
  }

  def setHandlers[F[_]: Sync](handlers: LogHandler*): F[Unit] = Sync[F].delay {
    handlers.foldLeft(Logger.root.clearHandlers())(_ withHandler _).replace()
  }

  private def consoleFormatter: Formatter = {
    import scribe.format._

    def messageColored: FormatBlock = FormatBlock { logRecord =>
      val color = logRecord.level match {
        case SL.Trace => Color.White
        case SL.Debug => Color.Green
        case SL.Info  => Color.Blue
        case SL.Warn  => Color.Yellow
        case SL.Error => Color.Red
        case _        => Color.Cyan
      }
      new ColoredOutput(color, message.format(logRecord))
    }

    val fileNameAbbreviated: FormatBlock = fileName.abbreviate(
      maxLength = 25,
      separator = '/',
      abbreviateName = true
    )
    formatter"$date $levelColoredPaddedRight [$threadName] - $messageColored$mdc ($fileNameAbbreviated:$line)$newLine"
  }

  private[log] def fromJbokLevel(level: Level): SL = level match {
    case Level.Trace => SL.Trace
    case Level.Debug => SL.Debug
    case Level.Info  => SL.Info
    case Level.Warn  => SL.Warn
    case Level.Error => SL.Error
  }
}
