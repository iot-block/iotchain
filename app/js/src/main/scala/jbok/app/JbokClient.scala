package jbok.app

import java.net.URI

import cats.effect.IO
import jbok.app.api.{PrivateAPI, PublicAPI}
import jbok.common.execution._
import jbok.network.client.{Client, WsClient}
import jbok.network.rpc.RpcClient
import scodec.bits.ByteVector

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.{Promise, UndefOr}
import scala.scalajs.js.annotation.{JSExport, JSExportAll, JSExportTopLevel}

@JSExportTopLevel("JbokClientClass")
@JSExportAll
case class JbokClient(uri: URI, client: Client[IO, String], admin: PrivateAPI[IO], public: PublicAPI[IO]) {
  def status: IO[Boolean] = client.haltWhenTrue.get.map(!_)
}

@JSExportTopLevel("JBokClient")
object JbokClient {
  import jbok.network.rpc.RpcServer._

  def apply(uri: URI): IO[JbokClient] =
    for {
      client <- WsClient[IO, String](uri)
      admin  = RpcClient(client).useAPI[PrivateAPI[IO]]
      public = RpcClient(client).useAPI[PublicAPI[IO]]
      _ <- client.start
    } yield JbokClient(uri, client, admin, public)

  @JSExport
  def webSocket(url: String): IO[JbokClient] = {
    val uri = new URI(url)
    apply(uri)
  }
}

@JSExportTopLevel("Option")
@JSExportAll
object JSOption {
  def None(): scala.None.type = scala.None

  def Some[A](value: A): scala.Some[A] = scala.Some(value)

  def UndefOr[A](opt: Option[A]): js.UndefOr[A] = opt.orUndefined
}

@JSExportTopLevel("BigInt")
@JSExportAll
object JSBigInt {
  def fromString(num: String): BigInt = BigInt(num)

  def toString(bi: BigInt): String = bi.toString(10)
}

object JSPromise {
  @JSExportTopLevel("Call")
  def call[A](f: Future[A]): Promise[A] = f.toJSPromise

  @JSExportTopLevel("Call")
  def call[A](f: IO[A]): Promise[A] = f.unsafeToFuture().toJSPromise
}

@JSExportTopLevel("ByteVector")
@JSExportAll
object JSByteVector {
  def toString(bv: ByteVector): String = bv.toHex

  def toArray(bv: ByteVector): js.Array[Byte] = bv.toArray.toJSArray

  def fromString(data: String): ByteVector = ByteVector.fromValidHex(data)

  def fromArray(ab: js.Array[Byte]): ByteVector = ByteVector(ab.toArray)
}
