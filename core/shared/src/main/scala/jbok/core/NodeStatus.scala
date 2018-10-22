package jbok.core
import java.net.InetSocketAddress

import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
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
      keyPair         <- fs2.async.refOf[F, Option[KeyPair]](None)
      rpcServer       <- fs2.async.refOf[F, Option[InetSocketAddress]](None)
      peerServer      <- fs2.async.refOf[F, Option[InetSocketAddress]](None)
      discoveryServer <- fs2.async.refOf[F, Option[InetSocketAddress]](None)
    } yield NodeStatus[F](keyPair, rpcServer, peerServer, discoveryServer)
}
