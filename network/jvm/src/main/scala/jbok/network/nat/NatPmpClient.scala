package jbok.network.nat

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import com.offbynull.portmapper.gateways.network.NetworkGateway
import com.offbynull.portmapper.gateways.network.internalmessages.KillNetworkRequest
import com.offbynull.portmapper.gateways.process.ProcessGateway
import com.offbynull.portmapper.gateways.process.internalmessages.KillProcessRequest
import com.offbynull.portmapper.mapper.{MappedPort, PortType}
import com.offbynull.portmapper.mappers.natpmp.NatPmpPortMapper

object NatPmpClient {
  def apply[F[_]](implicit F: Sync[F]): F[Nat[F]] =
    for {
      network <- F.delay(NetworkGateway.create)
      networkBus = network.getBus
      process <- F.delay(ProcessGateway.create)
      processBus = process.getBus
      mappers     <- F.delay(NatPmpPortMapper.identify(networkBus, processBus))
      mapper      <- F.delay(mappers.get(0))
      mappedPorts <- Ref.of[F, Map[Int, MappedPort]](Map.empty)
    } yield
      new Nat[F] {
        private[this] val log = org.log4s.getLogger("NatPmpClient")

        override def addMapping(internalPort: Int, externalPort: Int, lifetime: Long): F[Unit] =
          for {
            _ <- deleteMapping(externalPort)
            port <- F
              .delay(mapper.mapPort(PortType.TCP, internalPort, externalPort, lifetime))
              .handleErrorWith { e =>
                log.error(e)(s"add port mapping from ${internalPort} to ${externalPort} failed")
                F.raiseError(e)
              }
            _ <- mappedPorts.update(_ + (externalPort -> port))
          } yield ()

        override def deleteMapping(externalPort: Int): F[Unit] =
          for {
            portOpt <- mappedPorts.get.map(_.get(externalPort))
            _       <- portOpt.fold(F.unit)(port => F.delay(mapper.unmapPort(port)))
            _       <- mappedPorts.update(_ - externalPort)
          } yield ()

        override def stop: F[Unit] =
          F.delay {
            network.getBus.send(new KillNetworkRequest())
            process.getBus.send(new KillProcessRequest())
          }
      }
}
