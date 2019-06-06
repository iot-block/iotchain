package jbok.network

import java.nio.charset.StandardCharsets
import java.util.UUID

import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.{Chunk, Pipe, Stream}
import io.circe._
import jbok.codec.rlp.implicits._
import jbok.codec.rlp.{RlpCodec, RlpEncoded}
import org.http4s
import org.http4s.{MediaType, Method, Status, Uri}
import scodec.bits.{BitVector, ByteVector}

sealed trait ContentType
object ContentType {
  case object Binary extends ContentType
  case object Json   extends ContentType
  case object Text   extends ContentType

  implicit val codec: RlpCodec[ContentType] = RlpCodec.gen[ContentType]
}

sealed trait Message[F[_]] {
  def id: UUID

  def body: RlpEncoded

  def contentType: ContentType

  def contentLength: Long = body.bytes.length

  def as[A](implicit F: Sync[F], codec: RlpCodec[A]): F[A] =
    F.fromEither(body.decoded[A])
}

object Message {
  implicit def codec[G[_]]: RlpCodec[Message[G]] = RlpCodec.gen[Message[G]]

  def encodeBytes[F[_]](message: Message[F]): RlpEncoded =
    message.encoded

  def decodeBytes[F[_]: Sync](bytes: RlpEncoded): F[Message[F]] =
    Sync[F].fromEither(bytes.decoded[Message[F]])

  def decodeChunk[F[_]: Sync](chunk: Chunk[Byte]): F[Message[F]] =
    decodeBytes[F](RlpEncoded.coerce(BitVector(chunk.toArray)))

  def encodePipe[F[_]: Effect]: Pipe[F, Message[F], Byte] = { ms: Stream[F, Message[F]] =>
    scodec.stream.encode
      .many[Message[F]](RlpCodec[Message[F]])
      .encode(ms)
      .flatMap(bits => {
        val bytes = bits.bytes
        Stream.chunk(Chunk.byteVector(bytes))
      })
  }

  def decodePipe[F[_]: Effect]: Pipe[F, Byte, Message[F]] = { bytes: Stream[F, Byte] =>
    val bits = bytes.mapChunks(chunk => Chunk(ByteVector(chunk.toByteBuffer).toBitVector))
    bits through scodec.stream.decode.pipe[F, Message[F]]
  }
}

final case class Request[F[_]](
    id: UUID,
    method: String,
    contentType: ContentType,
    body: RlpEncoded
) extends Message[F] {
  override def toString: String =
    s"Request(id=${id}, method=${method}, contentType=${contentType}, contentLength=${contentLength})"
}

object Request {
  implicit def codec[G[_]]: RlpCodec[Request[G]] = RlpCodec.gen[Request[G]]

  def binary[F[_], A](method: String, body: RlpEncoded, id: UUID = UUID.randomUUID()): Request[F] =
    Request(id, method, ContentType.Binary, body)

  def json[F[_]](method: String, body: Json, id: UUID = UUID.randomUUID()): Request[F] =
    text[F](method, body.noSpaces, id)

  def text[F[_]](method: String, body: String, id: UUID = UUID.randomUUID()): Request[F] =
    Request[F](id, method, ContentType.Text, body.getBytes(StandardCharsets.UTF_8).encoded)

  def toHttp4s[F[_]](req: Request[F]): http4s.Request[F] = {
    val uri = Uri.unsafeFromString("")
    http4s.Request[F](Method.POST, uri).withEntity(req.body.byteArray)
  }

  def fromHttp4s[F[_]: Sync](req: http4s.Request[F]): F[Request[F]] =
    req.as[Array[Byte]].map { arr =>
      Request[F](
        UUID.randomUUID(),
        req.uri.path,
        ContentType.Binary,
        RlpEncoded.coerce(BitVector(arr))
      )
    }
}

final case class Response[F[_]](
    id: UUID,
    code: Int,
    message: String,
    contentType: ContentType,
    body: RlpEncoded
) extends Message[F] {

  def isSuccess: Boolean =
    code < 300

  override def toString: String =
    s"Response(id=${id}, code=${code}, message=${message}, contentType=${contentType}, contentLength=${contentLength})"
}

object Response {
  implicit def codec[G[_]]: RlpCodec[Response[G]] = RlpCodec.gen[Response[G]]

  def toHttp4s[F[_]](res: Response[F]): http4s.Response[F] =
    http4s.Response[F](status = Status(res.code, res.message)).withEntity(res.body.byteArray)

  def fromHttp4s[F[_]: Sync](res: http4s.Response[F]): F[Response[F]] =
    res.as[Array[Byte]].map { arr =>
      Response[F](
        UUID.randomUUID(),
        res.status.code,
        res.status.reason,
        res.contentType.map(_.mediaType) match {
          case Some(MediaType.application.json) => ContentType.Json
          case Some(MediaType.text.plain)       => ContentType.Text
          case _                                => ContentType.Binary
        },
        RlpEncoded.coerce(BitVector(arr))
      )
    }
}
