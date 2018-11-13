package jbok.network.nat

import cats.effect.Sync
import cats.implicits._
import org.bitlet.weupnp.GatewayDiscover

object NatUpnpClient {
  def apply[F[_]](implicit F: Sync[F]): F[Nat[F]] =
    for {
      discover <- F.delay(new GatewayDiscover)
      _      = discover.discover()
      device = discover.getValidGateway
    } yield
      new Nat[F] {
        private[this] val log = org.log4s.getLogger("NatUpnpClient")

        override def addMapping(internalPort: Int, externalPort: Int, lifetime: Long): F[Unit] =
          for {
            _ <- deleteMapping(externalPort)
            result <- F.delay(
              device.addPortMapping(externalPort, internalPort, device.getLocalAddress.getHostAddress, "TCP", "jbok"))
            _ = log.debug(s"add port mapping result ${result}")
          } yield ()

        override def deleteMapping(externalPort: Int): F[Unit] =
          F.delay(device.deletePortMapping(externalPort, "TCP"))

        override def stop: F[Unit] =
          F.unit
      }
}
