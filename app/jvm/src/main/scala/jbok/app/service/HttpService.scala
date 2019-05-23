package jbok.app.service

import cats.effect.{ConcurrentEffect, Resource, Timer}
import io.circe.Json
import cats.implicits._
import fs2._
import javax.net.ssl.SSLContext
import jbok.network.http.server.middleware.{GzipMiddleware, LoggerMiddleware, MetricsMiddleware}
import jbok.core.config.ServiceConfig
import jbok.core.api._
import jbok.network.rpc.RpcService
import jbok.network.rpc.http.Http4sRpcServer
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.server.{SSLClientAuthMode, Server}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import scala.concurrent.duration._

final class HttpService[F[_]](
    config: ServiceConfig,
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

  val rpcService: RpcService[F, Json] =
    RpcService[F, Json]
      .mount(account)
      .mount(admin)
      .mount(block)
      .mount(contract)
      .mount(miner)
      .mount(personal)
      .mount(transaction)

  val routes: HttpRoutes[F] = Http4sRpcServer.routes(rpcService)

  val methodConfig = CORSConfig(anyOrigin = true, anyMethod = false, allowedMethods = Some(Set("GET", "POST")), allowCredentials = true, maxAge = 1.day.toSeconds)

  private val builder: F[BlazeServerBuilder[F]] = {
    val httpApp = for {
      exportRoute <- MetricsMiddleware.exportService[F]
      withMetrics <- MetricsMiddleware[F](routes)
      withLogger = LoggerMiddleware[F]((withMetrics <+> exportRoute).orNotFound)
      withCORS = CORS(withLogger)
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

    sslOpt match {
      case Some(ssl) => builder.map(_.withSSLContext(ssl, SSLClientAuthMode.NotRequested))
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
