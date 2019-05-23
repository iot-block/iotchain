package jbok.network.rpc

trait RpcTransport[F[_], P] { transport =>
  def fetch(request: RpcRequest[P]): F[RpcResponse[P]]

//  final def mapK[G[_]](f: F[P] => G[P]): RpcTransport[G, P] =
//    new RpcTransport[G, P] {
//      def fetch(request: RpcRequest[P]): G[P] = f(transport.fetch(request))
//    }
}

object RpcTransport {
  def apply[F[_], P](f: RpcRequest[P] => F[RpcResponse[P]]): RpcTransport[F, P] =
    new RpcTransport[F, P] {
      def fetch(request: RpcRequest[P]): F[RpcResponse[P]] = f(request)
    }
}
