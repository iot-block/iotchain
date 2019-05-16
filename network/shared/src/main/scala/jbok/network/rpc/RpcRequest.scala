package jbok.network.rpc

import io.circe.generic.JsonCodec

@JsonCodec
final case class RpcRequest[A](path: List[String], payload: A)
