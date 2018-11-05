package jbok.core.nat

import cats.effect.IO
import com.offbynull.portmapper.gateways.network.NetworkGateway
import com.offbynull.portmapper.gateways.network.internalmessages.KillNetworkRequest
import com.offbynull.portmapper.gateways.process.ProcessGateway
import com.offbynull.portmapper.gateways.process.internalmessages.KillProcessRequest
import com.offbynull.portmapper.mapper.{MappedPort, PortMapper, PortType}
import com.offbynull.portmapper.mappers.natpmp.NatPmpPortMapper
import com.offbynull.portmapper.mappers.upnpigd.UpnpIgdPortMapper
import org.apache.commons.collections4.CollectionUtils

sealed abstract class NatType
case object NatPMP extends NatType
case object NatUPnP extends NatType

case class NatClient(
  newwork:NetworkGateway,
  processor:ProcessGateway,
  mapper:PortMapper){
  val log = org.log4s.getLogger

  def addMapping(protocol: String, internalPort: Int,externalPort: Int): IO[Option[MappedPort]] = {
    IO{mapper.mapPort(PortType.TCP,internalPort,externalPort,Long.MaxValue)}
      .map(Some(_))
      .handleErrorWith(err => {
        log.error(err)(err.getMessage)
        IO.pure(Option.empty)
      })
  }

  def deleteMapping(mappedPort: MappedPort): IO[Boolean] = {
    IO{mapper.unmapPort(mappedPort)}
      .map(_ => true)
      .handleErrorWith(err => {
        log.error(err)(err.getMessage)
        IO.pure(false)
      })
  }

  def stop(): IO[Unit] ={
    IO{
      newwork.getBus.send(new KillNetworkRequest())
      processor.getBus.send(new KillProcessRequest())
    }
  }

}

object NatClient{
  def apply(natType: NatType): IO[Option[NatClient]] = {
    for {
      network <- IO(NetworkGateway.create)
      networkBus = network.getBus
      process <- IO(ProcessGateway.create)
      processBus = process.getBus
      mappers = natType match {
        case NatPMP => NatPmpPortMapper.identify(networkBus,processBus)
        case NatUPnP => UpnpIgdPortMapper.identify(networkBus)
      }
      nat = if (CollectionUtils.isEmpty(mappers)) None else Some(NatClient(network,process,mappers.get(0)))
    }yield nat
  }

  def main(args: Array[String]): Unit = {
    println(1)
  }
}
