package jbok.core

import cats.Functor
import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import jbok.core.peer.PeerSelectStrategy.PeerSelectStrategy
import jbok.network.Message

package object peer {
  type PeerService[F[_]] = Kleisli[F, PeerRequest[F], PeerResponse[F]]

  type PeerRoutes[F[_]] = Kleisli[OptionT[F, ?], PeerRequest[F], PeerResponse[F]]

  object PeerRoutes {
    def of[F[_]](pf: PartialFunction[PeerRequest[F], F[PeerResponse[F]]])(implicit F: Sync[F]): PeerRoutes[F] =
      Kleisli(req => OptionT(F.suspend(pf.lift(req).sequence)))
  }

  implicit def peerRoutesSyntax[F[_]: Functor](
      service: Kleisli[OptionT[F, ?], PeerRequest[F], PeerResponse[F]]): PeerRoutesOps[F] =
    new PeerRoutesOps[F](service)

  final class PeerRoutesOps[F[_]: Functor](self: Kleisli[OptionT[F, ?], PeerRequest[F], PeerResponse[F]]) {
    def orNil: PeerService[F] = Kleisli(a => self.run(a).getOrElse(Nil))
  }

  final case class PeerRequest[F[_]](peer: Peer[F], message: Message[F])
  type PeerResponse[F[_]] = List[(PeerSelectStrategy[F], Message[F])]
}
