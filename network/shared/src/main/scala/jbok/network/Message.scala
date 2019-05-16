package jbok.network

import java.net.{InetSocketAddress, URI}
import java.nio.charset.StandardCharsets
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
import org.http4s
import org.http4s.{EntityDecoder, MediaType, Method, Status, Uri}
import scodec.Codec
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

  def body: Array[Byte]

  def contentType: ContentType

  def contentLength: Int = body.length

  def as[A](implicit F: Sync[F], codec: RlpCodec[A]): F[A] =
    F.fromEither(codec.decode(BitVector(body)).toEither.leftMap(err => new Exception(err.messageWithContext)).map(_.value))

//  def bodyAs[A](implicit F: Sync[F], b: RlpCodec[A], j: Decoder[A]): F[A] = contentType match {
//    case ContentType.Binary => binaryBodyAs[A]
//    case ContentType.Json   => jsonBodyAs[A]
//  }
//
//  def binaryBodyAs[A](implicit F: Sync[F], C: RlpCodec[A]): F[A] =
//    F.delay(C.decode(body.bits).require.value)
//
//  def jsonBodyAs[A](implicit F: Sync[F], C: Decoder[A]): F[A] =
//    F.delay(RlpCodec.decode[String](body.bits).require.value)
//      .flatMap(s => F.fromEither(C.decodeJson(parse(s).getOrElse(Json.Null))))
//
//  def bodyAsJson(implicit F: Sync[F]): F[Json] = contentType match {
//    case ContentType.Binary =>
//      Json.fromString(body.toBase64).pure[F]
//    case ContentType.Json =>
//      F.delay(RlpCodec.decode[String](body.bits).require.value).map(str => parse(str).getOrElse(Json.Null))
//  }
//
//  def asJson(implicit F: Sync[F]): F[Json]
//
//  def asBytes(implicit F: Sync[F]): F[ByteVector] =
//    Message.encodeBytes[F](this)
//
//  def asChunk(implicit F: Sync[F]): F[Chunk[Byte]] =
//    asBytes.map(ByteVectorChunk.apply)
}

object Message {
  implicit def codec[F[_]]: RlpCodec[Message[F]] = RlpCodec.gen[Message[F]]

  val emptyBody: ByteVector = ().asValidBytes

  def encodeBytes[F[_]: Sync](message: Message[F]): F[ByteVector] =
    Sync[F].delay(RlpCodec.encode(message).require.bytes)

  def decodeBytes[F[_]: Sync](bytes: ByteVector): F[Message[F]] =
    Sync[F].delay(RlpCodec.decode[Message[F]](bytes.bits).require.value)

  def decodeChunk[F[_]: Sync](chunk: Chunk[Byte]): F[Message[F]] =
    Sync[F].delay(RlpCodec.decode[Message[F]](BitVector(chunk.toArray)).require.value)

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
    body: Array[Byte]
) extends Message[F] {

//  override def asJson(implicit F: Sync[F]): F[Json] =
//    bodyAsJson.map(
//      bodyJson =>
//        Json.obj(
//          ("id", id.asJson),
//          ("method", method.asJson),
//          ("contentType", contentType.asJson),
//          ("body", bodyJson)
//      ))

  override def toString: String =
    s"Request(id=${id}, method=${method}, contentType=${contentType}, contentLength=${contentLength})"
}

object Request {
  implicit def codec[F[_]]: RlpCodec[Request[F]] = RlpCodec.gen[Request[F]]

  def binary[F[_], A](method: String, body: ByteVector, id: UUID = UUID.randomUUID()): Request[F] =
    Request(id, method, ContentType.Binary, body.toArray)

  def json[F[_]](method: String, body: Json, id: UUID = UUID.randomUUID()): Request[F] =
    text[F](method, body.noSpaces, id)

  def text[F[_]](method: String, body: String, id: UUID = UUID.randomUUID()): Request[F] =
    Request[F](id, method, ContentType.Text, body.getBytes(StandardCharsets.UTF_8))

//
//  def fromJson[F[_]](json: Json)(implicit F: Sync[F]): F[Request[F]] = Sync[F].fromEither {
//    for {
//      id          <- json.hcursor.downField("id").as[UUID]
//      method      <- json.hcursor.downField("method").as[String]
//      contentType <- json.hcursor.downField("contentType").as[ContentType]
//      body <- contentType match {
//        case ContentType.Binary => json.hcursor.downField("body").as[String].map(s => ByteVector.fromValidBase64(s))
//        case ContentType.Json   => json.hcursor.get[Json]("body").map(s => s.noSpaces.asValidBytes)
//      }
//    } yield {
//      Request[F](id, method, contentType, body)
//    }
//  }

  def toHttp4s[F[_]](req: Request[F]): http4s.Request[F] = {
    val uri = Uri.unsafeFromString("")
    http4s.Request[F](Method.POST, uri).withEntity(req.body)
  }

  def fromHttp4s[F[_]: Sync](req: http4s.Request[F]): F[Request[F]] =
    req.as[Array[Byte]].map { bytes =>
      Request[F](
        UUID.randomUUID(),
        req.uri.path,
        ContentType.Binary,
        bytes
      )
    }
}

final case class Response[F[_]](
    id: UUID,
    code: Int,
    message: String,
    contentType: ContentType,
    body: Array[Byte],
) extends Message[F] {

//  override def asJson(implicit F: Sync[F]): F[Json] =
//    bodyAsJson.map(
//      bodyJson =>
//        Json.obj(
//          ("id", id.asJson),
//          ("code", code.asJson),
//          ("message", message.asJson),
//          ("contentType", contentType.asJson),
//          ("body", bodyJson)
//      ))

  def isSuccess: Boolean =
    code < 300

  override def toString: String =
    s"Response(id=${id}, code=${code}, message=${message}, contentType=${contentType}, contentLength=${contentLength})"
}

object Response {
  implicit def codec[F[_]]: RlpCodec[Response[F]] = RlpCodec.gen[Response[F]]

  def toHttp4s[F[_]](res: Response[F]): http4s.Response[F] =
    http4s.Response[F](status = Status(res.code, res.message)).withEntity(res.body)

  def fromHttp4s[F[_]: Sync](res: http4s.Response[F]): F[Response[F]] =
    res.as[Array[Byte]].map { bytes =>
      Response[F](
        UUID.randomUUID(),
        res.status.code,
        res.status.reason,
        res.contentType.map(_.mediaType) match {
          case Some(MediaType.application.json) => ContentType.Json
          case Some(MediaType.text.plain)       => ContentType.Text
          case _                                => ContentType.Binary
        },
        bytes
      )
    }

  def ok[F[_], A](id: UUID, a: A)(implicit F: Sync[F], C: RlpCodec[A]): F[Response[F]] =
    a.asBytes[F].map(body => Response(id, Status.Ok.code, Status.Ok.reason, ContentType.Binary, body.toArray))
//
//  def json[F[_]](id: UUID, code: Int, message: String, body: Json): Response[F] =
//    Response[F](id, contentType = ContentType.Json, body = body.noSpaces.asValidBytes)
//
//  def badRequest[F[_]](id: UUID)(implicit F: Sync[F]): Response[F] =
//    Response[F](id, 400, "Bad Request")
//
//  def notFound[F[_]](id: UUID, method: String)(implicit F: Sync[F]): Response[F] =
//    Response[F](id, 404, s"Method ${method} Not Found")
//
//  def internalError[F[_]](id: UUID)(implicit F: Sync[F]): Response[F] =
//    Response[F](id, 500, "Internal Server Error")

//  def fromJson[F[_]](json: Json)(implicit F: Sync[F]): F[Response[F]] = Sync[F].fromEither {
//    for {
//      id          <- json.hcursor.downField("id").as[UUID]
//      code        <- json.hcursor.downField("code").as[Int]
//      message     <- json.hcursor.downField("message").as[String]
//      contentType <- json.hcursor.downField("contentType").as[ContentType]
//      body <- contentType match {
//        case ContentType.Binary => json.hcursor.downField("body").as[String].map(s => ByteVector.fromValidBase64(s))
//        case ContentType.Json   => json.hcursor.get[Json]("body").map(s => s.noSpaces.asValidBytes)
//      }
//    } yield {
//      Response[F](id, code, message, contentType, body)
//    }
//  }
}
