package jbok.core

import cats.Functor
import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import jbok.core.messages.Message
import jbok.core.peer.PeerSelectStrategy.PeerSelectStrategy

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

  final case class Request[F[_]](peer: Peer[F], message: Message)
  type Response[F[_]] = List[(PeerSelectStrategy[F], Message)]
}
