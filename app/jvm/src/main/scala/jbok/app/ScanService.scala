package jbok.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import jbok.core.models.Address
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import scodec.bits.ByteVector

import scala.concurrent.duration._
import jbok.common.execution._

final case class TransactionQueryRes(code: Int, msg: String, data: List[TransactionQueryData])
final case class TransactionQueryData(
    id: Long,
    txHash: String,
    nonce: Int,
    fromAddress: String,
    toAddress: String,
    value: String,
    payload: String,
    v: String,
    r: String,
    s: String,
    gasUsed: String,
    gasPrice: String,
    blockNumber: Long,
    blockHash: String,
    location: Int
)

object ScanService {
  object AddressParamMatcher extends QueryParamDecoderMatcher[String]("address")
  object StartParamMatcher   extends OptionalQueryParamDecoderMatcher[Long]("start")
  object EndParamMatcher     extends OptionalQueryParamDecoderMatcher[Long]("end")
  object PageParamMatcher    extends OptionalQueryParamDecoderMatcher[Int]("page")
  object SizeParamMatcher    extends OptionalQueryParamDecoderMatcher[Int]("size")

  val transactionService = HttpRoutes.of[IO] {
    case req @ GET -> Root / "address" :?
          AddressParamMatcher(address) +&
            StartParamMatcher(start) +&
            EndParamMatcher(end) +&
            PageParamMatcher(page) +&
            SizeParamMatcher(size) =>
      for {
        _ <- println(req.params).pure[IO]
        data <- ScanServiceStore.select(
          Address(ByteVector.fromValidHex(address)),
          start.getOrElse(0),
          end,
          page.getOrElse(1),
          size.getOrElse(5)
        )
        resp <- Ok(TransactionQueryRes(200, "success", data).asJson)
      } yield resp
  }

  val httpApp =
    Router("/v1/transaction" -> transactionService).orNotFound

  def run(args: List[String]): IO[ExitCode] = {
    val builder = BlazeServerBuilder[IO]
      .bindLocal(10086)
      .withHttpApp(httpApp)
      .withoutBanner
      .withIdleTimeout(60.seconds)

    builder.serve.compile.drain.as(ExitCode.Success)
  }
}
