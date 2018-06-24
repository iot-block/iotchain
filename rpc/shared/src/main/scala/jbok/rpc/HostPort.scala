package jbok.rpc

import io.circe.generic.JsonCodec

@JsonCodec
case class HostPort(host: String, port: Option[Int] = None) {
  override def toString: String = port match {
    case Some(p) => s"$host:${p}"
    case _ => host
  }
}

object HostPort {
  def apply(host: String, port: Int): HostPort = HostPort(host, Some(port))
}
