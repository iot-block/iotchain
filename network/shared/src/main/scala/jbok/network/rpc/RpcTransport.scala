package jbok.network.rpc

trait RpcTransport[F[_], P] { transport =>
  def fetch(request: RpcRequest[P]): F[P]

  final def mapK[G[_]](f: F[P] => G[P]): RpcTransport[G, P] =
    new RpcTransport[G, P] {
      def fetch(request: RpcRequest[P]): G[P] = f(transport.fetch(request))
    }
}

object RpcTransport {
  def apply[F[_], Payload](f: RpcRequest[Payload] => F[Payload]): RpcTransport[F, Payload] =
    new RpcTransport[F, Payload] {
      def fetch(request: RpcRequest[Payload]): F[Payload] = f(request)
    }
}
