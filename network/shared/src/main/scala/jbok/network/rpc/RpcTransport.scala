package jbok.network.rpc

trait RpcTransport[F[_], P] { transport =>
  def fetch(request: RpcRequest[P]): F[RpcResponse[P]]
}

object RpcTransport {
  def apply[F[_], P](f: RpcRequest[P] => F[RpcResponse[P]]): RpcTransport[F, P] =
    new RpcTransport[F, P] {
      def fetch(request: RpcRequest[P]): F[RpcResponse[P]] = f(request)
    }
}
