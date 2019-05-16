package jbok.app.service

import cats.Id
import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, Timer}
import cats.implicits._
import io.circe.generic.auto._
import fs2._
import jbok.app.config.ServiceConfig
import jbok.app.service.middleware._
import jbok.app.service.store.{BlockStore, TransactionStore}
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.implicits._
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{HttpApp, HttpRoutes}
import scodec.bits.ByteVector
import tsec.mac.jca.HMACSHA256

import scala.concurrent.duration._

final case class JbokResp[A](code: Int, msg: String, data: A)
final class ScanService[F[_]](config: ServiceConfig, blockStore: BlockStore[F], txStore: TransactionStore[F])(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F])
    extends SwaggerSupport[F] {

  private val transactionRhoRoutes: RhoRoutes[F] = new RhoRoutes[F] {
    val address = pathVar[String]("address", "blockchain account address, 20 bytes hex string")
    val page    = paramD[Int]("page", "page number", 1, (p: Int) => p >= 1)
    val size    = paramD[Int]("size", "page size", 5, (s: Int) => s >= 1)
    val hash    = pathVar[String]("hash", "sha256 hash hex string")

    "Get all transactions, ORDER BY blockNumber, index DESC" **
      GET / "transactions" +? page & size |>> { (page: Int, size: Int) =>
      for {
        data <- txStore.findAllTxs(page, size)
        resp <- Ok(JbokResp(200, "", data))
      } yield resp
    }

    "Get transactions by sender or receiver address" **
      GET / "transactions" / "address" / address +? page & size |>> { (address: String, page: Int, size: Int) =>
      for {
        txList <- txStore.findTransactionsByAddress(
          address,
          page,
          size
        )
        resp <- Ok(JbokResp(200, "", txList))
      } yield resp
    }

    "Get transaction by hash" **
      GET / "transactions" / "hash" / hash |>> { hash: String =>
      for {
        txOpt <- txStore.findTransactionByHash(hash)
        resp  <- Ok(JbokResp(200, "", txOpt))
      } yield resp
    }
  }

  private val blockRhoRoutes: RhoRoutes[F] = new RhoRoutes[F] {
    val page   = paramD[Int]("page", "page number", 1, (p: Int) => p >= 1)
    val size   = paramD[Int]("size", "page size", 5, (s: Int) => s >= 1)
    val number = pathVar[Long]("number", "block number")
    val hash   = pathVar[String]("hash", "block hash sha256 hex string")

    "Get all blocks, ORDER BY number DESC" **
      GET / "blocks" +? page & size |>> { (page: Int, size: Int) =>
      for {
        data <- blockStore.findAllBlocks(page, size)
        resp <- Ok(JbokResp(200, "", data))
      } yield resp
    }

    "Get block by hash" **
      GET / "blocks" / "hash" / hash |>> { hash: String =>
      for {
        data <- blockStore.findBlockByHash(hash)
        resp <- Ok(JbokResp(200, "", data))
      } yield resp
    }

    "Get block by number" **
      GET / "blocks" / "number" / number |>> { number: Long =>
      for {
        data <- blockStore.findBlockByNumber(number)
        resp <- Ok(JbokResp(200, "", data))
      } yield resp
    }
  }

  private val key = HMACSHA256.buildKey[Id](ByteVector.fromValidHex(config.secretKey).toArray)

  private val v1routes = (transactionRhoRoutes and blockRhoRoutes).toRoutes(SwaggerMiddleware.swaggerMiddleware[F]("/v1"))

  private val authedV1Routes = HmacAuthMiddleware(key)(v1routes)

  private val routes: HttpRoutes[F] =
    Router(
      "/"   -> StaticFilesService.routes[F],
      "/v1" -> authedV1Routes
    )

  private val httpApp: F[HttpApp[F]] =
    for {
      metered <- MetricsMiddleware(routes)
      gzipped = GzipMiddleware(metered.orNotFound)
      throttled <- ThrottleMiddleware(config.qps, 1.second)(gzipped)
      logged = LoggingMiddleware(throttled)
    } yield logged

  def serve: Stream[F, ExitCode] =
    Stream
      .eval(httpApp)
      .flatMap(app => {
        BlazeServerBuilder[F]
          .bindHttp(port = config.port, host = config.host)
          .withHttpApp(app)
          .withoutBanner
          .withIdleTimeout(60.seconds)
          .serve
      })
}
//
//object ScanService {
//  implicit val scheduler             = Scheduler.global
//  implicit val options: Task.Options = Task.defaultOptions
//  implicit val taskEff               = new CatsConcurrentEffectForTask
//
//  def serve(config: ServiceConfig): Stream[IO, Unit] =
//    for {
//      _     <- Stream.eval(Migration.migrate(config.dbUrl))
//      store <- Stream.resource(ServiceStore.quill(config.dbUrl))
//      service = new ScanService(store, config)
//      _ <- service.serve
//    } yield ()
//}
