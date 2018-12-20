package jbok.common.log

import java.nio.file.Paths

import scribe.format.Formatter
import scribe.handler.LogHandler
import scribe.writer.FileWriter
import scribe.writer.file.LogPath

object ScribeLogPlatform {
  def fileHandler(directory: String, minimumLevel: Option[Level] = None): LogHandler = LogHandler(
    fileFormatter,
    FileWriter().nio
      .path(LogPath.simple("jbok.log", directory = Paths.get(directory)))
      .rolling(LogPath.daily(prefix = "jbok", directory = Paths.get(directory))),
    minimumLevel.map(ScribeLog.fromJbokLevel)
  )

  private def fileFormatter: Formatter = {
    import scribe.format._
    formatter"$date [$levelPaddedRight] [$threadName] - $message$mdc$newLine"
  }
}
