package jbok.network.nat

import cats.effect.Sync

sealed trait NatType
case object NatPMP  extends NatType
case object NatUPnP extends NatType

trait Nat[F[_]] {
  def addMapping(internalPort: Int, externalPort: Int, lifetime: Long): F[Unit]

  def deleteMapping(externalPort: Int): F[Unit]

  def stop: F[Unit]
}

object Nat {
  def apply[F[_]: Sync](natType: NatType): F[Nat[F]] =
    natType match {
      case NatPMP  => NatPmpClient[F]
      case NatUPnP => NatUpnpClient[F]
    }
}
