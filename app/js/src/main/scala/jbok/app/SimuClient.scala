package jbok.app

import java.net.URI

import cats.effect.IO
import jbok.app.api.SimulationAPI
import jbok.network.client.WsClient
import jbok.network.rpc.RpcClient
import jbok.common.execution._
import jbok.codec.rlp.implicits._

case class SimuClient(uri: URI, simulation: SimulationAPI)

object SimuClient {
  import jbok.network.rpc.RpcServer._
  def apply(url: String): IO[SimuClient] = {
    val uri: URI = new URI(url)
    for {
      client <- WsClient[IO, String](uri)
      simulation = RpcClient(client).useAPI[SimulationAPI]
      _ <- client.start
    } yield SimuClient(uri, simulation)
  }
}
