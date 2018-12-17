package jbok.common.log

sealed trait Level
object Level {
  case object Trace extends Level
  case object Debug extends Level
  case object Info  extends Level
  case object Warn  extends Level
  case object Error extends Level

  def fromName(name: String) = name.toLowerCase match {
    case "trace" => Level.Trace
    case "debug" => Level.Debug
    case "info"  => Level.Info
    case "warn"  => Level.Warn
    case "error" => Level.Error
    case _       => throw new Exception(s"invalid log level name ${name}")
  }
}
