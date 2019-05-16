package jbok.app.service

import cats.effect.{ConcurrentEffect, Resource, Timer}
import io.circe.Json
import fs2._
import javax.net.ssl.SSLContext
import jbok.app.config.ServiceConfig
import jbok.core.api._
import jbok.network.rpc.RpcService
import jbok.network.rpc.http.Http4sRpcServer
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.server.{SSLClientAuthMode, Server}
import org.http4s.server.blaze.BlazeServerBuilder

final class HttpService[F[_]](
    config: ServiceConfig,
    account: AccountAPI[F],
    admin: AdminAPI[F],
    block: BlockAPI[F],
    contract: ContractAPI[F],
    personal: PersonalAPI[F],
    transaction: TransactionAPI[F],
    sslOpt: Option[SSLContext]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  import jbok.codec.impl.circe._
  import _root_.io.circe.generic.auto._
  import jbok.codec.json.implicits._

  val rpcService: RpcService[F, Json] =
    RpcService[F, Json]
      .mount(account)
      .mount(admin)
      .mount(block)
      .mount(contract)
      .mount(personal)
      .mount(transaction)

  val routes: HttpRoutes[F] = Http4sRpcServer.routes(rpcService)

  private val builder = {
    val b = BlazeServerBuilder[F]
      .withHttpApp(routes.orNotFound)
      .withNio2(true)
      .enableHttp2(config.enableHttp2)
      .withWebSockets(config.enableWebsockets)
      .bindHttp(config.port, config.host)

    sslOpt match {
      case Some(ssl) => b.withSSLContext(ssl, SSLClientAuthMode.NotRequested)
      case None      => b.enableHttp2(false)
    }
  }

  val resource: Resource[F, Server[F]] =
    builder.resource

  val stream: Stream[F, Unit] =
    if (config.enable) {
      builder.serve.drain
    } else {
      Stream.empty
    }
}
