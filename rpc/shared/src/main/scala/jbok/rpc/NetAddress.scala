package jbok.rpc

import io.circe.generic.JsonCodec

@JsonCodec
case class Address(host: String, port: Option[Int] = None, scheme: String) {
  override def toString: String = port match {
    case Some(p) => s"$scheme://$host:$p"
    case _ => s"$scheme://$host"
  }
}

object Address {
  val defaultScheme: String = "ws"

  def apply(host: String, port: Int): Address = Address(host, Some(port), defaultScheme)

  def apply(host: String): Address = Address(host, None, defaultScheme)
}
