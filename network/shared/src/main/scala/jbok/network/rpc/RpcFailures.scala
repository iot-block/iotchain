package jbok.network.rpc

sealed trait ServerFailure
object ServerFailure {
  final case class PathNotFound(path: List[String]) extends ServerFailure {
    override def toString: String = s"PathNotFound path=${path.mkString("/")}"
  }
  final case class InternalError(ex: Throwable) extends ServerFailure
  final case class DecodeError(ex: Throwable)   extends ServerFailure
}

sealed trait ClientFailure
object ClientFailure {
  final case class TransportError(ex: Throwable) extends ClientFailure
  final case class DecoderError(ex: Throwable)   extends ClientFailure
}
