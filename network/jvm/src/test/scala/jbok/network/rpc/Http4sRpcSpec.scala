package jbok.network.rpc

import cats.effect.IO
import io.circe.Json
import jbok.common.CommonSpec
import jbok.network.rpc.http.{Http4sRpcServer, Http4sRpcTransport}
import org.http4s.Uri
import cats.implicits._
import org.http4s.circe.CirceEntityCodec._
import org.scalatest.Assertion
import jbok.codec.impl.circe._
import io.circe.generic.auto._

class Http4sRpcSpec extends CommonSpec {
  val impl    = new TestApiImpl
  val service = RpcService[IO, Json].mount[TestAPI[IO]](impl)
  val server  = Http4sRpcServer.server[IO](service)

  def transport(uri: Uri) = new Http4sRpcTransport[IO, Json](uri)

  def client(uri: Uri) = RpcClient[IO, Json](transport(uri)).use[TestAPI[IO]]

  def assertIO[A](io1: IO[A], io2: IO[A]): IO[Assertion] =
    (io1, io2).mapN(_ shouldBe _)

  "Http4sRpcService" should {
    "impl service and client" in {
      val p = server.use { s =>
        val c = client(s.baseUri)
        assertIO(c.foo, impl.foo) >>
          assertIO(c.bar, impl.bar) >>
          assertIO(c.qux("oho", 42), impl.qux("oho", 42))
      }
      p.unsafeRunSync()
    }
  }
}
