package jbok.network

import java.util.UUID

import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Chunk.ByteVectorChunk
import fs2.{Chunk, Pipe, Stream}
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.codec.json.implicits._
import scodec.Codec
import scodec.bits.{BitVector, ByteVector}

sealed trait ContentType
object ContentType {
  case object Binary extends ContentType
  case object Json   extends ContentType
}

sealed trait Message[F[_]] {
  def id: UUID

  def body: ByteVector

  def contentType: ContentType

  def contentLength: Long =
    body.length

  def bodyAs[A](implicit F: Sync[F], b: RlpCodec[A], j: Decoder[A]): F[A] = contentType match {
    case ContentType.Binary => binaryBodyAs[A]
    case ContentType.Json   => jsonBodyAs[A]
  }

  def binaryBodyAs[A](implicit F: Sync[F], C: RlpCodec[A]): F[A] =
    F.delay(C.decode(body.bits).require.value)

  def jsonBodyAs[A](implicit F: Sync[F], C: Decoder[A]): F[A] =
    F.delay(RlpCodec.decode[String](body.bits).require.value)
      .flatMap(s => F.fromEither(C.decodeJson(parse(s).getOrElse(Json.Null))))

  def bodyAsJson(implicit F: Sync[F]): F[Json] = contentType match {
    case ContentType.Binary =>
      Json.fromString(body.toBase64).pure[F]
    case ContentType.Json =>
      F.delay(RlpCodec.decode[String](body.bits).require.value).map(str => parse(str).getOrElse(Json.Null))
  }

  def asJson(implicit F: Sync[F]): F[Json]

  def asBytes(implicit F: Sync[F]): F[ByteVector] =
    Message.encodeBytes[F](this)

  def asChunk(implicit F: Sync[F]): F[Chunk[Byte]] =
    asBytes.map(ByteVectorChunk.apply)
}

object Message {
  implicit def codec[F[_]]: Codec[Message[F]] = RlpCodec[Message[F]]

  val emptyBody: ByteVector = ().asValidBytes

  def encodeBytes[F[_]: Sync](message: Message[F]): F[ByteVector] =
    Sync[F].delay(RlpCodec.encode(message).require.bytes)

  def decodeBytes[F[_]: Sync](bytes: ByteVector): F[Message[F]] =
    Sync[F].delay(RlpCodec.decode[Message[F]](bytes.bits).require.value)

  def decodeChunk[F[_]: Sync](chunk: Chunk[Byte]): F[Message[F]] =
    Sync[F].delay(RlpCodec.decode[Message[F]](BitVector(chunk.toArray)).require.value)

  def encodePipe[F[_]: Effect]: Pipe[F, Message[F], Byte] =
    _.evalMap(_.asChunk).flatMap(chunk => Stream.chunk(chunk))

  def decodePipe[F[_]: Effect]: Pipe[F, Byte, Message[F]] = _.chunks.flatMap { chunk =>
    val bits = BitVector(chunk.toArray)
    scodec.stream.decode.many[Message[F]](RlpCodec[Message[F]]).decode[F](bits)
  }
}

final case class Request[F[_]](
    id: UUID,
    method: String,
    contentType: ContentType = ContentType.Binary,
    body: ByteVector
) extends Message[F] {

  override def asJson(implicit F: Sync[F]): F[Json] =
    bodyAsJson.map(
      bodyJson =>
        Json.obj(
          ("id", id.asJson),
          ("method", method.asJson),
          ("contentType", contentType.asJson),
          ("body", bodyJson)
      ))

  override def toString: String =
    s"Request(method=${method}, body=${body}, id=${id})"
}

object Request {
  def binary[F[_], A](method: String, body: A, id: UUID = UUID.randomUUID())(implicit F: Sync[F],
                                                                             C: RlpCodec[A]): F[Request[F]] =
    body.asBytes[F].map(bytes => Request(id, method, ContentType.Binary, bytes))

  def json[F[_]](id: UUID, method: String, body: Json): Request[F] =
    Request[F](id, method, ContentType.Json, body.noSpaces.asValidBytes)

  def fromJson[F[_]](json: Json)(implicit F: Sync[F]): F[Request[F]] = Sync[F].fromEither {
    for {
      id          <- json.hcursor.downField("id").as[UUID]
      method      <- json.hcursor.downField("method").as[String]
      contentType <- json.hcursor.downField("contentType").as[ContentType]
      body <- contentType match {
        case ContentType.Binary => json.hcursor.downField("body").as[String].map(s => ByteVector.fromValidBase64(s))
        case ContentType.Json   => json.hcursor.get[Json]("body").map(s => s.noSpaces.asValidBytes)
      }
    } yield {
      Request[F](id, method, contentType, body)
    }
  }
}

final case class Response[F[_]](
    id: UUID,
    code: Int = 200,
    message: String = "",
    contentType: ContentType = ContentType.Binary,
    body: ByteVector = Message.emptyBody
) extends Message[F] {

  override def asJson(implicit F: Sync[F]): F[Json] =
    bodyAsJson.map(
      bodyJson =>
        Json.obj(
          ("id", id.asJson),
          ("code", code.asJson),
          ("message", message.asJson),
          ("contentType", contentType.asJson),
          ("body", bodyJson)
      ))

  def isSuccess: Boolean =
    code < 300

  override def toString: String =
    s"Response(code=${code}, message=${message}, body=${body}, id=${id})"
}

object Response {
  def ok[F[_], A](id: UUID, a: A)(implicit F: Sync[F], C: RlpCodec[A]): F[Response[F]] =
    a.asBytes[F].map(body => Response(id, contentType = ContentType.Binary, body = body))

  def json[F[_]](id: UUID, code: Int, message: String, body: Json): Response[F] =
    Response[F](id, contentType = ContentType.Json, body = body.noSpaces.asValidBytes)

  def badRequest[F[_]](id: UUID)(implicit F: Sync[F]): Response[F] =
    Response[F](id, 400, "Bad Request")

  def notFound[F[_]](id: UUID, method: String)(implicit F: Sync[F]): Response[F] =
    Response[F](id, 404, s"Method ${method} Not Found")

  def internalError[F[_]](id: UUID)(implicit F: Sync[F]): Response[F] =
    Response[F](id, 500, "Internal Server Error")

  def fromJson[F[_]](json: Json)(implicit F: Sync[F]): F[Response[F]] = Sync[F].fromEither {
    for {
      id          <- json.hcursor.downField("id").as[UUID]
      code        <- json.hcursor.downField("code").as[Int]
      message     <- json.hcursor.downField("message").as[String]
      contentType <- json.hcursor.downField("contentType").as[ContentType]
      body <- contentType match {
        case ContentType.Binary => json.hcursor.downField("body").as[String].map(s => ByteVector.fromValidBase64(s))
        case ContentType.Json   => json.hcursor.get[Json]("body").map(s => s.noSpaces.asValidBytes)
      }
    } yield {
      Response[F](id, code, message, contentType, body)
    }
  }
}
