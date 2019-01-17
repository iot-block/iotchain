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
import scodec.Codec
import scodec.bits.{BitVector, ByteVector}

sealed trait Message[F[_]] { self =>
  type Self <: Message[F] { type Self = self.Self }

  def id: UUID

  def body: ByteVector

  def contentLength: Long =
    body.length

  def bodyAs[A](implicit F: Sync[F], c: RlpCodec[A]): F[A] =
    F.delay(c.decode(body.bits).require.value)

  def bodyAsText(implicit F: Sync[F]): F[String] =
    F.delay(RlpCodec.decode[String](body.bits).require.value)

  def bodyAsJson(implicit F: Sync[F]): F[Json] =
    bodyAsText.map(str => parse(str).getOrElse(Json.Null))

  def asJson(implicit F: Sync[F]): F[Json]

  def asText(implicit F: Sync[F]): F[String] =
    asJson.map(_.spaces2)

  def encodeBytes(implicit F: Sync[F]): F[ByteVector] =
    Message.encodeBytes[F](this)

  def encodeChunk(implicit F: Sync[F]): F[Chunk[Byte]] =
    encodeBytes.map(ByteVectorChunk.apply)

  protected def changed(
      id: UUID,
      body: ByteVector
  ): Self
}

object Message {
  implicit def codec[F[_]]: Codec[Message[F]] = RlpCodec[Message[F]]

  val emptyBody: ByteVector = RlpCodec.encode("").require.bytes

  def encodeBytes[F[_]: Sync](message: Message[F]): F[ByteVector] =
    Sync[F].delay(RlpCodec.encode(message).require.bytes)

  def decodeBytes[F[_]: Sync](bytes: ByteVector): F[Message[F]] =
    Sync[F].delay(RlpCodec.decode[Message[F]](bytes.bits).require.value)

  def decodeChunk[F[_]: Sync](chunk: Chunk[Byte]): F[Message[F]] =
    Sync[F].delay(RlpCodec.decode[Message[F]](BitVector(chunk.toArray)).require.value)

  def encodePipe[F[_]: Effect]: Pipe[F, Message[F], Byte] =
    _.evalMap(_.encodeChunk).flatMap(chunk => Stream.chunk(chunk))

  def decodePipe[F[_]: Effect]: Pipe[F, Byte, Message[F]] = _.chunks.flatMap { chunk =>
    val bits = BitVector(chunk.toArray)
    scodec.stream.decode.many[Message[F]](RlpCodec[Message[F]]).decode[F](bits)
  }
}

final case class Request[F[_]](
    id: UUID,
    method: String,
    body: ByteVector
) extends Message[F] {
  type Self = Request[F]

  override protected def changed(id: UUID, body: ByteVector): Self =
    copy(id = id, body = body)

  override def asJson(implicit F: Sync[F]): F[Json] =
    bodyAsJson.map(
      bodyJson =>
        Json.obj(
          ("id", id.asJson),
          ("method", method.asJson),
          ("body", bodyJson)
      ))

  override def toString: String =
    s"Request(method=${method}, body=${body}, id=${id})"
}

object Request {
  def apply[F[_], A](method: String, body: A, id: UUID = UUID.randomUUID())(implicit F: Sync[F],
                                                                            C: RlpCodec[A]): F[Request[F]] =
    body.asBytesF[F].map(bytes => Request(id, method, bytes))

  def withTextBody[F[_]](id: UUID, method: String, body: String): Request[F] =
    Request[F](id, method, RlpCodec.encode(body).require.bytes)

  def withJsonBody[F[_]](id: UUID, method: String, body: Json): Request[F] =
    withTextBody[F](id, method, body.noSpaces)

  def fromJson[F[_]](json: Json)(implicit F: Sync[F]): F[Request[F]] = Sync[F].fromEither {
    for {
      id     <- json.hcursor.downField("id").as[UUID]
      method <- json.hcursor.downField("method").as[String]
      body   <- json.hcursor.getOrElse[Json]("body")(Json.Null)
    } yield {
      Request.withJsonBody[F](id, method, body)
    }
  }

  def fromText[F[_]](text: String)(implicit F: Sync[F]): F[Request[F]] =
    F.fromEither(parse(text)).flatMap(json => fromJson[F](json))
}

final case class Response[F[_]](
    id: UUID,
    code: Int = 200,
    message: String = "",
    body: ByteVector = Message.emptyBody
) extends Message[F] {
  override type Self = Response[F]

  override protected def changed(id: UUID, body: ByteVector): Self =
    copy(id = id, body = body)

  override def asJson(implicit F: Sync[F]): F[Json] =
    bodyAsJson.map(
      bodyJson =>
        Json.obj(
          ("id", id.asJson),
          ("code", code.asJson),
          ("message", message.asJson),
          ("body", bodyJson)
      ))

  def isSuccess: Boolean =
    code < 300

  override def toString: String =
    s"Response(code=${code}, message=${message}, body=${body}, id=${id})"
}

object Response {
  def ok[F[_], A](id: UUID, a: A)(implicit F: Sync[F], C: RlpCodec[A]): F[Response[F]] =
    a.asBytesF[F].map(body => Response(id, body = body))

  def withTextBody[F[_]](id: UUID, code: Int, message: String, body: String): Response[F] =
    Response[F](id, body = RlpCodec.encode(body).require.bytes)

  def withJsonBody[F[_]](id: UUID, code: Int, message: String, body: Json): Response[F] =
    withTextBody[F](id, code, message, body.noSpaces)

  def badRequest[F[_]](id: UUID)(implicit F: Sync[F]): Response[F] =
    Response[F](id, 400, "Bad Request")

  def notFound[F[_]](id: UUID, method: String)(implicit F: Sync[F]): Response[F] =
    Response[F](id, 404, s"Method ${method} Not Found")

  def internalError[F[_]](id: UUID)(implicit F: Sync[F]): Response[F] =
    Response[F](id, 500, "Internal Server Error")

  def fromJson[F[_]](json: Json)(implicit F: Sync[F]): F[Response[F]] = Sync[F].fromEither {
    for {
      id      <- json.hcursor.downField("id").as[UUID]
      code    <- json.hcursor.downField("code").as[Int]
      message <- json.hcursor.downField("message").as[String]
      body    <- json.hcursor.getOrElse[Json]("body")(Json.Null)
    } yield {
      Response.withJsonBody[F](id, code, message, body)
    }
  }

  def fromText[F[_]](text: String)(implicit F: Sync[F]): F[Response[F]] =
    F.fromEither(parse(text)).flatMap(json => fromJson[F](json))
}
