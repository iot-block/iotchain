package jbok.common.log

import java.nio.file.Paths

import scribe.handler.LogHandler
import scribe.writer.FileWriter
import scribe.writer.file.LogPath

object LogJVM {
  def fileHandler(directory: String, minimumLevel: Option[Level] = None): LogHandler = LogHandler(
    Log.fileFormatter,
    FileWriter().nio
      .path(LogPath.simple("jbok.log", directory = Paths.get(directory)))
      .rolling(LogPath.daily(prefix = "jbok", directory = Paths.get(directory))),
    minimumLevel.map(Log.fromJbokLevel)
  )
}
