package jbok.core

import cats.Functor
import cats.data.{Kleisli, OptionT}
import jbok.core.messages.Message
import cats.effect.Sync
import jbok.core.models.Block
import cats.implicits._

import scala.util.Try

package object peer {
  type PeerService[F[_]] = Kleisli[F, Request[F], Response[F]]

  type PeerRoutes[F[_]] = Kleisli[OptionT[F, ?], Request[F], Response[F]]

  object PeerRoutes {
    def of[F[_]](pf: PartialFunction[Request[F], F[Response[F]]])(implicit F: Sync[F]): PeerRoutes[F] =
      Kleisli(req => OptionT(F.suspend(pf.lift(req).sequence)))
  }

  implicit def peerRoutesSyntax[F[_]: Functor](
      service: Kleisli[OptionT[F, ?], Request[F], Response[F]]): PeerRoutesOps[F] =
    new PeerRoutesOps[F](service)

  final class PeerRoutesOps[F[_]: Functor](self: Kleisli[OptionT[F, ?], Request[F], Response[F]]) {
    def orNil: PeerService[F] = Kleisli(a => self.run(a).getOrElse(Nil))
  }

  final case class Request[F[_]](peer: Peer[F], peerSet: PeerSet[F], message: Message)
  type Response[F[_]] = List[(Peer[F], Message)]

  final case class PeerSet[F[_]](connected: F[List[Peer[F]]])(implicit F: Sync[F]) {
    def peersWithoutBlock(block: Block): F[List[Peer[F]]] =
      connected.flatMap { peers =>
        peers
          .traverse[F, Option[Peer[F]]] { peer =>
            peer.hasBlock(block.header.hash).map {
              case true  => None
              case false => Some(peer)
            }
          }
          .map(_.flatten)
      }

    def bestPeer: F[Option[Peer[F]]] =
      for {
        peers <- connected.flatMap(_.traverse(p => p.status.get.map(_.bestNumber).map(bn => bn -> p)))
        best = Try(peers.maxBy(_._1)._2).toOption
      } yield best
  }

  object PeerSet {
    def empty[F[_]](implicit F: Sync[F]): PeerSet[F] = PeerSet[F](F.pure(Nil))
  }
}
