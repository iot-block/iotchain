package jbok.network.rpc

final case class RpcRequest[A](path: List[String], payload: A)
