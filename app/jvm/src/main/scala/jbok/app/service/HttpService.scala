package jbok.app.service

import cats.effect.{ConcurrentEffect, Resource, Timer}
import io.circe.Json
import cats.implicits._
import fs2._
import javax.net.ssl.SSLContext
import jbok.network.http.server.middleware.{CORSMiddleware, GzipMiddleware, LoggerMiddleware, MetricsMiddleware}
import jbok.core.config.ServiceConfig
import jbok.core.api._
import jbok.crypto.ssl.SSLConfig
import jbok.network.rpc.RpcService
import jbok.network.rpc.http.Http4sRpcServer
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.server.{SSLClientAuthMode, Server}
import org.http4s.server.blaze.BlazeServerBuilder

final class HttpService[F[_]](
    config: ServiceConfig,
    sslConfig: SSLConfig,
    account: AccountAPI[F],
    admin: AdminAPI[F],
    block: BlockAPI[F],
    contract: ContractAPI[F],
    miner: MinerAPI[F],
    personal: PersonalAPI[F],
    transaction: TransactionAPI[F],
    sslOpt: Option[SSLContext]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  import jbok.codec.impl.circe._
  import _root_.io.circe.generic.auto._
  import jbok.codec.json.implicits._

  val rpcService: RpcService[F, Json] = {
    var service = RpcService[F, Json]
    if (config.apis.contains("account")) service = service.mount(account) else ()
    if (config.apis.contains("admin")) service = service.mount(admin) else ()
    if (config.apis.contains("block")) service = service.mount(block) else ()
    if (config.apis.contains("contract")) service = service.mount(contract) else ()
    if (config.apis.contains("miner")) service = service.mount(miner) else ()
    if (config.apis.contains("personal")) service = service.mount(personal) else ()
    if (config.apis.contains("transaction")) service = service.mount(transaction) else ()
    service
  }

  val routes: HttpRoutes[F] = Http4sRpcServer.routes(rpcService)

  private val builder: F[BlazeServerBuilder[F]] = {
    val httpApp = for {
      exportRoute <- MetricsMiddleware.exportService[F]
      withMetrics <- MetricsMiddleware[F](routes, config.enableMetrics)
      withLogger = LoggerMiddleware[F](config.logHeaders, config.logBody)((withMetrics <+> exportRoute).orNotFound)
      withCORS   = CORSMiddleware[F](withLogger, config.allowedOrigins)
      app        = GzipMiddleware[F](withCORS)
    } yield app

    val builder = httpApp.map { app =>
      BlazeServerBuilder[F]
        .withHttpApp(app)
        .withNio2(true)
        .enableHttp2(config.enableHttp2)
        .withWebSockets(config.enableWebsockets)
        .bindHttp(config.port, config.host)
    }

    val sslLClientAuthMode = sslConfig.clientAuth match {
      case "NotRequested" => SSLClientAuthMode.NotRequested
      case "Requested"    => SSLClientAuthMode.Requested
      case "Required"     => SSLClientAuthMode.Requested
      case x              => throw new IllegalArgumentException(s"SSLClientAuthMode ${x} is not supported")
    }

    sslOpt match {
      case Some(ssl) => builder.map(_.withSSLContext(ssl, sslLClientAuthMode))
      case None      => builder.map(_.enableHttp2(false))
    }
  }

  val resource: Resource[F, Server[F]] =
    Resource.liftF(builder).flatMap(_.resource)

  val stream: Stream[F, Unit] =
    if (config.enable) {
      Stream.eval(builder).flatMap(_.serve).drain
    } else {
      Stream.empty
    }
}
