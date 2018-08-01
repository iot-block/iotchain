package jbok.network

import io.circe.generic.JsonCodec
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._

@JsonCodec
case class NetAddress(host: String, port: Option[Int] = None, scheme: String) {
  override def toString: String = port match {
    case Some(p) => s"$scheme://$host:$p"
    case _ => s"$scheme://$host"
  }
}

object NetAddress {
  implicit val codec: RlpCodec[NetAddress] = implicitly[RlpCodec[NetAddress]]

  val defaultScheme: String = "ws"

  def apply(host: String, port: Int): NetAddress = NetAddress(host, Some(port), defaultScheme)

  def apply(host: String): NetAddress = NetAddress(host, None, defaultScheme)
}
