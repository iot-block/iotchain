package jbok.network.nat

import cats.effect.IO
import com.offbynull.portmapper.gateways.network.NetworkGateway
import com.offbynull.portmapper.gateways.network.internalmessages.KillNetworkRequest
import com.offbynull.portmapper.gateways.process.ProcessGateway
import com.offbynull.portmapper.gateways.process.internalmessages.KillProcessRequest
import com.offbynull.portmapper.mapper.{MappedPort, PortMapper, PortType}
import com.offbynull.portmapper.mappers.natpmp.NatPmpPortMapper
import com.offbynull.portmapper.mappers.upnpigd.UpnpIgdPortMapper
import org.apache.commons.collections4.CollectionUtils
import com.offbynull.portmapper.mapper.PortType
import java.lang.reflect.Constructor
import java.net.InetAddress


case class NatPmpClient(
                         newwork:NetworkGateway,
                         processor:ProcessGateway,
                         mapper:PortMapper,
                         natType:NatType) extends Nat {
  val log = org.log4s.getLogger

  def addMapping(internalPort: Int,externalPort: Int,lifetime:Long): IO[Boolean] = {
    for {
      _ <- deleteMapping(internalPort,externalPort)
      mappedPort = mapper.mapPort(PortType.TCP,internalPort,externalPort, lifetime)
    }yield true
  }

  def deleteMapping(internalPort: Int,externalPort: Int): IO[Boolean] = {
    IO{
      val mappedPort = initMappedPort(internalPort,externalPort)
      mapper.unmapPort(mappedPort.asInstanceOf[MappedPort])
    }.map(_ => true)
  }

  private def initMappedPort(internalPort: Int,externalPort: Int): MappedPort ={
    val obj = natType match {
      case NatPMP => {
        val cls = Class.forName("com.offbynull.portmapper.mappers.natpmp.NatPmpMappedPort")
        val constructor = cls.getDeclaredConstructors()(0)
        constructor.setAccessible(true)
        val obj = constructor.newInstance(Int.box(internalPort), Int.box(externalPort), InetAddress.getLocalHost, PortType.TCP, Int.box(1000))
        obj
      }
      case NatUPnP => {
        val cls = Class.forName("com.offbynull.portmapper.mappers.upnpigd.PortMapperMappedPort")
        val constructor = cls.getDeclaredConstructors()(0)
        constructor.setAccessible(true)
        val obj = constructor.newInstance(Int.box(internalPort), Int.box(externalPort), InetAddress.getLocalHost, PortType.TCP, Int.box(1000))
        obj
      }
    }
    obj.asInstanceOf[MappedPort]
  }

  def stop(): IO[Unit] ={
    IO{
      newwork.getBus.send(new KillNetworkRequest())
      processor.getBus.send(new KillProcessRequest())
    }
  }

}

object NatPmpClient{
  def apply(natType: NatType): IO[NatPmpClient] = {
    for {
      network <- IO(NetworkGateway.create)
      networkBus = network.getBus
      process <- IO(ProcessGateway.create)
      processBus = process.getBus
      mappers = natType match {
        case NatPMP => NatPmpPortMapper.identify(networkBus,processBus)
        case NatUPnP => UpnpIgdPortMapper.identify(networkBus)
      }
      nat = NatPmpClient(network,process,mappers.get(0),natType)
    }yield nat
  }
}
