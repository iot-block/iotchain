package jbok.core.api

import java.net.URI

import cats.effect._
import io.circe.Json
import javax.net.ssl.SSLContext
import jbok.network.http.HttpTransport
import jbok.network.http.client.HttpClients
import jbok.network.http.client.HttpClients.withMiddlewares
import jbok.network.rpc.RpcClient

object JbokClientPlatform {
  import jbok.codec.impl.circe._
  import io.circe.generic.auto._
  import jbok.codec.json.implicits._

  def resource[F[_]](url: String, ssl: Option[SSLContext] = None)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, JbokClient[F]] =
    HttpClients.okHttp[F](ssl).evalMap(withMiddlewares[F]).map { client =>
      val transport = new HttpTransport[F](url, client)
      val rpc       = RpcClient(transport)

      new JbokClient[F] {
        override def uri: URI                       = new URI(url)
        override def client: RpcClient[F, Json]     = rpc
        override def account: AccountAPI[F]         = rpc.use[AccountAPI[F]]
        override def admin: AdminAPI[F]             = rpc.use[AdminAPI[F]]
        override def block: BlockAPI[F]             = rpc.use[BlockAPI[F]]
        override def contract: ContractAPI[F]       = rpc.use[ContractAPI[F]]
        override def personal: PersonalAPI[F]       = rpc.use[PersonalAPI[F]]
        override def transaction: TransactionAPI[F] = rpc.use[TransactionAPI[F]]
      }
    }
}
