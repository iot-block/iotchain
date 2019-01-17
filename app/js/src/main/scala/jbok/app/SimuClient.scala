package jbok.app

import java.net.URI

import cats.effect.IO
import jbok.app.api.SimulationAPI
import jbok.common.execution._
import jbok.network.client.WsClient
import jbok.network.rpc.RpcClient

final case class SimuClient(uri: URI, simulation: SimulationAPI)

object SimuClient {
  def apply(uri: URI): IO[SimuClient] =
    for {
      client <- WsClient[IO](uri)
      simulation = RpcClient(client).useAPI[SimulationAPI]
      _ <- client.start
    } yield SimuClient(uri, simulation)
}
