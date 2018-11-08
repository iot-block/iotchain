package jbok.core.nat
import cats.effect.IO
import org.bitlet.weupnp.{GatewayDevice, GatewayDiscover}

class NatUpnpClient() extends Nat {
  val log = org.log4s.getLogger

  private def discoverGateway(): IO[GatewayDevice] ={
    for {
      discover <- IO(new GatewayDiscover)
      _ = discover.discover()
      device = discover.getValidGateway
    }yield device
  }

  override def addMapping(internalPort: Int, externalPort: Int, lifetime: Long): IO[Boolean] = {
    for {
      device <- discoverGateway()
      localAddress = device.getLocalAddress
      externalIPAddress = device.getExternalIPAddress
      _ = device.deletePortMapping(externalPort,"TCP")
      result = device.addPortMapping(externalPort,internalPort,localAddress.getHostAddress,"TCP","jbok")
    }yield result
  }

  override def deleteMapping(internalPort: Int, externalPort: Int): IO[Boolean] = {
    for {
      device <- discoverGateway()
      result = device.deletePortMapping(externalPort,"TCP")
    }yield result
  }

  override def stop(): IO[Unit] = IO.unit

}

object NatUpnpClient{
  def apply(): IO[NatUpnpClient] = IO.pure(new NatUpnpClient())
}
