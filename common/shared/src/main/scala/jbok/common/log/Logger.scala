package jbok.common.log

import _root_.scribe.{LogRecord, Level => SL}
import cats.Monad
import cats.effect.Sync
import scribe.format.Formatter
import scribe.handler.LogHandler
import scribe.output.{Color, ColoredOutput, LogOutput, TextOutput}
import scribe.writer.Writer

import scala.language.experimental.macros

trait Logger[F[_]] {
  final def t(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def d(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def i(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def w(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def e(message: => String): F[Unit] = macro Macros.autoLevel1[F]

  final def trace(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def debug(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def info(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def warn(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def error(message: => String): F[Unit] = macro Macros.autoLevel1[F]

  final def t(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def d(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def i(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def w(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def e(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]

  final def trace(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def debug(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def info(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def warn(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def error(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
}

object Logger {
  def apply[F[_]: Sync]: Logger[F] = new Logger[F] {}

  def consoleHandler(minimumLevel: Option[Level] = None): LogHandler = LogHandler(
    consoleFormatter,
    minimumLevel = minimumLevel.map(fromJbokLevel)
  )

  def setRootLevel[F[_]](level: Level)(implicit F: Sync[F]): F[Unit] = F.delay {
    scribe.Logger.root.withMinimumLevel(fromJbokLevel(level)).replace()
  }

  def setRootHandlers[F[_]](handlers: LogHandler*)(implicit F: Sync[F]): F[Unit] = F.delay {
    handlers.foldLeft(scribe.Logger.root.clearHandlers())(_ withHandler _).replace()
  }

  def setRootHandlers[F[_]](writer: Writer)(implicit F: Sync[F]): F[Unit] = F.delay {
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
