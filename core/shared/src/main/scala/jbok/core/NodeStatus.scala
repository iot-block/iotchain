package jbok.core
import java.net.InetSocketAddress

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.crypto.signature.KeyPair

case class NodeStatus[F[_]](
    keyPair: Ref[F, Option[KeyPair]],
    rpcAddr: Ref[F, Option[InetSocketAddress]],
    peerAddr: Ref[F, Option[InetSocketAddress]],
    discoveryAddr: Ref[F, Option[InetSocketAddress]]
)

object NodeStatus {
  def apply[F[_]: Sync]: F[NodeStatus[F]] =
    for {
      keyPair         <- Ref.of[F, Option[KeyPair]](None)
      rpcServer       <- Ref.of[F, Option[InetSocketAddress]](None)
      peerServer      <- Ref.of[F, Option[InetSocketAddress]](None)
      discoveryServer <- Ref.of[F, Option[InetSocketAddress]](None)
    } yield NodeStatus[F](keyPair, rpcServer, peerServer, discoveryServer)
}
