package jbok.common.log

import _root_.scribe.{LogRecord, Level => SL}
import cats.effect.IO
import scribe.format.Formatter
import scribe.handler.LogHandler
import scribe.output.{Color, ColoredOutput, LogOutput, TextOutput}
import scribe.writer.Writer

object Log extends Logger[IO] {
  def consoleHandler(minimumLevel: Option[Level] = None): LogHandler = LogHandler(
    consoleFormatter,
    minimumLevel = minimumLevel.map(fromJbokLevel)
  )

  def setRootLevel(level: Level): IO[Unit] = IO {
    scribe.Logger.root.withMinimumLevel(fromJbokLevel(level)).replace()
  }

  def setRootHandlers(handlers: LogHandler*): IO[Unit] = IO {
    handlers.foldLeft(scribe.Logger.root.clearHandlers())(_ withHandler _).replace()
  }

  def setRootHandlers(writer: Writer): IO[Unit] = IO {
    scribe.Logger.root
      .clearHandlers()
      .withHandler(writer = writer)
      .replace()
  }

  def consoleFormatter: Formatter = {
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

    object LevelInitial extends FormatBlock {
      override def format[M](record: LogRecord[M]): LogOutput = new TextOutput(record.level.name.take(1))
    }

    def levelInitialColored: FormatBlock = FormatBlock { logRecord =>
      val color = logRecord.level match {
        case SL.Trace => Color.White
        case SL.Debug => Color.Green
        case SL.Info  => Color.Blue
        case SL.Warn  => Color.Yellow
        case SL.Error => Color.Red
        case _        => Color.Cyan
      }
      new ColoredOutput(color, LevelInitial.format(logRecord))
    }

    val fileNameAbbreviated: FormatBlock = fileName.abbreviate(
      maxLength = 25,
      separator = '/',
      abbreviateName = true
    )

    formatter"$levelInitialColored $dateFull - $messageColored$mdc [$className]($fileNameAbbreviated:$line)"
  }

  def fileFormatter: Formatter = {
    import scribe.format._
    formatter"$dateFull $levelPaddedRight [$threadName] - $message$mdc$newLine"
  }

  private[log] def fromJbokLevel(level: Level): SL = level match {
    case Level.Trace => SL.Trace
    case Level.Debug => SL.Debug
    case Level.Info  => SL.Info
    case Level.Warn  => SL.Warn
    case Level.Error => SL.Error
  }
}
