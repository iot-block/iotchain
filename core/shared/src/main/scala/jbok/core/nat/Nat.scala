package jbok.core.nat

import cats.effect.IO

sealed abstract class NatType
case object NatPMP extends NatType
case object NatUPnP extends NatType

trait Nat {
  def addMapping(internalPort: Int,externalPort: Int,lifetime:Long): IO[Boolean]
  def deleteMapping(internalPort: Int,externalPort: Int): IO[Boolean]
  def stop(): IO[Unit]
}

object Nat{
  def apply(natType: NatType): IO[Nat] = {
    natType match {
      case NatPMP => NatPmpClient(natType)
      case NatUPnP => NatUpnpClient()
    }
  }
}