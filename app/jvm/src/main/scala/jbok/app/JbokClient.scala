package jbok.app

import java.net.URI

import cats.effect._
import io.circe.Json
import jbok.network.http.{HttpClients, HttpTransport}
import jbok.network.rpc.RpcClient
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI, SimulationAPI}

final case class JbokClient[F[_]](
    uri: URI,
    client: RpcClient[F, Json],
    public: PublicAPI[F],
    personal: PersonalAPI[F],
    admin: AdminAPI[F],
    simulation: SimulationAPI[F]
)

object JbokClient {
  import jbok.codec.impl.circe._
  import io.circe.generic.auto._
  import jbok.codec.json.implicits._

  def apply[F[_]](uri: URI)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], clock: Clock[F]): Resource[F, JbokClient[F]] =
    HttpClients.okHttp[F].map { client =>
      val transport = new HttpTransport[F](uri.toString, client)
      val rpcClient = RpcClient(transport)
      val public    = rpcClient.use[PublicAPI[F]]
      val personal  = rpcClient.use[PersonalAPI[F]]
      val admin     = rpcClient.use[AdminAPI[F]]
      val sim       = rpcClient.use[SimulationAPI[F]]
      JbokClient(uri, rpcClient, public, personal, admin, sim)
    }
}
