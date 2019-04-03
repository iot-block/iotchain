package jbok.common.log

sealed trait Level
object Level {
  case object Trace extends Level
  case object Debug extends Level
  case object Info  extends Level
  case object Warn  extends Level
  case object Error extends Level

  def fromName(name: String): Level = name.toLowerCase match {
    case "t" | "trace" => Level.Trace
    case "d" | "debug" => Level.Debug
    case "i" | "info"  => Level.Info
    case "w" | "warn"  => Level.Warn
    case "e" | "error" => Level.Error
    case _             => throw new Exception(s"invalid log level name ${name}")
  }
}
