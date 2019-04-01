package jbok.app.service

import cats.effect.{ExitCode, IO}
import io.circe.generic.auto._
import fs2._
import jbok.app.service.middlewares.Swagger
import jbok.app.service.store.ServiceStore
import jbok.common.execution._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.implicits._
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.syntax.io._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.duration._

final case class OhoResp[A](code: Int, msg: String, data: A)
class ScanService(store: ServiceStore[IO]) {

  val transactionRhoRoutes: RhoRoutes[IO] = new RhoRoutes[IO] {
    val address = pathVar[String]("address", "blockchain account address, 20 bytes hex string")
    val page    = paramD[Int]("page", "page number", 1, (p: Int) => p >= 1)
    val size    = paramD[Int]("size", "page size", 5, (s: Int) => s >= 1)
    val hash    = pathVar[String]("hash", "sha256 hash hex string")

    "Get all transactions, ORDER BY blockNumber, index DESC" **
      GET / "transactions" +? page & size |>> { (page: Int, size: Int) =>
      for {
        data <- store.transactionStore.findAllTxs(page, size)
        resp <- Ok(OhoResp(200, "", data))
      } yield resp
    }

    "Get transactions by sender or receiver address" **
      GET / "transactions" / "address" / address +? page & size |>> { (address: String, page: Int, size: Int) =>
      for {
        txList <- store.transactionStore.findTransactionsByAddress(
          address,
          page,
          size
        )
        resp <- Ok(OhoResp(200, "", txList))
      } yield resp
    }

    "Get transaction by hash" **
      GET / "transactions" / "hash" / hash |>> { hash: String =>
      for {
        txOpt <- store.transactionStore.findTransactionByHash(hash)
        resp  <- Ok(OhoResp(200, "", txOpt))
      } yield resp
    }
  }

  val blockRhoRoutes: RhoRoutes[IO] = new RhoRoutes[IO] {
    val page   = paramD[Int]("page", "page number", 1, (p: Int) => p >= 1)
    val size   = paramD[Int]("size", "page size", 5, (s: Int) => s >= 1)
    val number = pathVar[Long]("number", "block number")
    val hash   = pathVar[String]("hash", "block hash sha256 hex string")

    "Get all blocks, ORDER BY number DESC" **
      GET / "blocks" +? page & size |>> { (page: Int, size: Int) =>
      for {
        data <- store.blockStore.findAllBlocks(page, size)
        resp <- Ok(OhoResp(200, "", data))
      } yield resp
    }

    "Get block by hash" **
      GET / "blocks" / "hash" / hash |>> { hash: String =>
      for {
        data <- store.blockStore.findBlockByHash(hash)
        resp <- Ok(OhoResp(200, "", data))
      } yield resp
    }

    "Get block by number" **
      GET / "blocks" / "number" / number |>> { number: Long =>
      for {
        data <- store.blockStore.findBlockByNumber(number)
        resp <- Ok(OhoResp(200, "", data))
      } yield resp
    }
  }

  val v1routes = (transactionRhoRoutes and blockRhoRoutes).toRoutes(Swagger.swaggerMiddleware("/v1"))

  val httpApp =
    Router(
      "/"   -> StaticFilesService.routes,
      "/v1" -> v1routes
    ).orNotFound

  def serve(port: Int): Stream[IO, ExitCode] =
    BlazeServerBuilder[IO]
      .bindLocal(port)
      .withHttpApp(httpApp)
      .withoutBanner
      .withIdleTimeout(60.seconds)
      .serve
}