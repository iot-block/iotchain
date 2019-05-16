package jbok.network.rpc

trait RpcLogHandler[F[_]] {
  def logRequest[A](path: List[String], arguments: Product, result: F[A]): F[A]
}

object RpcLogHandler {
  def requestLogLine(path: List[String]): String =
    logLine(path, None, None)

  def requestLogLine(path: List[String], arguments: Product): String =
    logLine(path, Some(arguments), None)

  def requestLogLine(path: List[String], arguments: Product, result: Any): String =
    logLine(path, Some(arguments), Some(result))

  def requestLogLine(path: List[String], arguments: Option[Product], result: Any): String =
    logLine(path, arguments, Some(result))

  private def logLine(path: List[String], arguments: Option[Product], result: Option[Any]): String = {
    val pathString = path.mkString(".")
    val argString  = "(" + arguments.fold(List.empty[Any])(_.productIterator.toList).mkString(",") + ")"
    val request    = pathString + argString
    result.fold(request)(result => s"$request, result: $result")
  }
}
