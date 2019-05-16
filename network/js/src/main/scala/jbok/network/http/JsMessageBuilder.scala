package jbok.network.http

import java.nio.ByteBuffer

import cats.effect.{Async, IO}
import org.scalajs.dom.{Blob, Event, FileReader, UIEvent}

import scala.concurrent.Promise
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.scalajs.js.typedarray._
import scala.scalajs.js.|

trait JsMessageBuilder[F[_], P] {
  import JsMessageBuilder._

  def responseType: String

  def pack(payload: P): Message

  def unpack(msg: Message): F[Option[P]]
}

object JsMessageBuilder {
  type Message = String | ArrayBuffer | Blob

  implicit def JsMessageBuilderString[F[_]](implicit F: Async[F]): JsMessageBuilder[F, String] = new JsMessageBuilder[F, String] {
    val responseType = ""

    def pack(payload: String): Message = payload

    def unpack(msg: Message): F[Option[String]] = (msg: Any) match {
      case s: String => F.pure(Some(s))
      case b: Blob   => readBlob[F, String, String](_.readAsText(b))(identity)
      case _         => F.pure(None)
    }
  }

  implicit def JsMessageBuilderByteBuffer[F[_]](implicit F: Async[F]): JsMessageBuilder[F, ByteBuffer] = new JsMessageBuilder[F, ByteBuffer] {
    val responseType = "arraybuffer"

    def pack(payload: ByteBuffer): Message = payload.arrayBuffer.slice(payload.position, payload.limit)

    def unpack(msg: Message): F[Option[ByteBuffer]] = (msg: Any) match {
      case a: ArrayBuffer => F.pure(Option(TypedArrayBuffer.wrap(a)))
      case b: Blob        => readBlob[F, ArrayBuffer, ByteBuffer](_.readAsArrayBuffer(b))(TypedArrayBuffer.wrap(_))
      case _              => F.pure(None)
    }
  }

  private def readBlob[F[_], R, W](doRead: FileReader => Unit)(conv: R => W)(implicit F: Async[F]): F[Option[W]] = {
    val promise = Promise[Option[W]]()
    val reader  = new FileReader
    reader.onload = (_: UIEvent) => {
      val s = reader.result.asInstanceOf[R]
      promise.success(Option(conv(s)))
    }
    reader.onerror = (_: Event) => {
      promise.success(None)
    }
    doRead(reader)
    F.liftIO(IO.fromFuture(IO(promise.future)))
  }
}
