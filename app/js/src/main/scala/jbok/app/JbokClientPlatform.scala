package jbok.app

import java.net.URI

import cats.effect.{Clock, ConcurrentEffect}
import io.circe.Json
import jbok.network.http.HttpTransport
import jbok.network.rpc.RpcClient
import jbok.core.api._

object JbokClientPlatform {
  def apply[F[_]](url: String)(implicit F: ConcurrentEffect[F], clock: Clock[F]): JbokClient[F] = {
    val transport = HttpTransport[F](url)
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
