package jbok.app

import java.net.URI

import cats.effect.{Clock, ConcurrentEffect, IO}
import jbok.network.http.HttpTransport
import jbok.network.rpc.RpcClient
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI, SimulationAPI}

final case class JbokClient(
    uri: URI,
    client: RpcClient[IO, String],
    public: PublicAPI[IO],
    personal: PersonalAPI[IO],
    admin: AdminAPI[IO],
    simulation: SimulationAPI[IO]
)

object JbokClient {
  def apply(uri: URI)(implicit F: ConcurrentEffect[IO], clock: Clock[IO]): JbokClient = {
    val transport = HttpTransport[IO](uri.toString)
    val rpcClient = RpcClient(transport)
    val public    = rpcClient.use[PublicAPI[IO]]
    val personal  = rpcClient.use[PersonalAPI[IO]]
    val admin     = rpcClient.use[AdminAPI[IO]]
    val sim       = rpcClient.use[SimulationAPI[IO]]
    JbokClient(uri, rpcClient, public, personal, admin, sim)
  }
}
