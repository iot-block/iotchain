package jbok.network.rpc

trait RpcTransport[F[_], Payload] { transport =>
  def fetch(request: RpcRequest[Payload]): F[Payload]

  final def map[G[_]](f: F[Payload] => G[Payload]): RpcTransport[G, Payload] =
    new RpcTransport[G, Payload] {
      def fetch(request: RpcRequest[Payload]): G[Payload] = f(transport.fetch(request))
    }
}

object RpcTransport {
  def apply[F[_], Payload](f: RpcRequest[Payload] => F[Payload]): RpcTransport[F, Payload] =
    new RpcTransport[F, Payload] {
      def fetch(request: RpcRequest[Payload]): F[Payload] = f(request)
    }
}
