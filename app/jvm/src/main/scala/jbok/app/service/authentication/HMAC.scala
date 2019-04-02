package jbok.app.service.authentication
import java.time.Instant

import cats.effect.IO
import cats.implicits._
import jbok.app.service.middleware.HmacAuthError
import tsec.common._
import tsec.mac.MAC
import tsec.mac.jca.{HMACSHA256, MacSigningKey}

object HMAC {
  def sign(toMac: Array[Byte], key: MacSigningKey[HMACSHA256]): IO[MAC[HMACSHA256]] =
    HMACSHA256.sign[IO](toMac, key)

  def signToHex(toMac: Array[Byte], key: MacSigningKey[HMACSHA256]): IO[String] =
    sign(toMac, key).map(_.toHexString)

  def signToB64(toMac: Array[Byte], key: MacSigningKey[HMACSHA256]): IO[String] =
    sign(toMac, key).map(_.toB64String)

  def signToB64Url(toMac: Array[Byte], key: MacSigningKey[HMACSHA256]): IO[String] =
    sign(toMac, key).map(_.toB64UrlString)

  def verify(toMac: Array[Byte], mac: MAC[HMACSHA256], key: MacSigningKey[HMACSHA256]): IO[Boolean] =
    HMACSHA256.verifyBool[IO](toMac, mac, key)

  def verifyEither(
      toMac: Array[Byte],
      mac: MAC[HMACSHA256],
      key: MacSigningKey[HMACSHA256]
  ): Either[Throwable, Unit] =
    verify(toMac, mac, key).unsafeRunSync() match {
      case true => Right(())
      case false => Left(new Exception())
    }

  object http {
    private def concat(method: String, url: String, datetime: String): String =
      method + "\n" + url + "\n" + datetime

    def signForQuery(
        method: String,
        url: String,
        datetime: String,
        key: MacSigningKey[HMACSHA256]
    ): IO[String] = {
      val stringToSign = concat(method, url, datetime)
      signToB64Url(stringToSign.utf8Bytes, key)
    }

    def signForHeader(
        method: String,
        url: String,
        datetime: String,
        key: MacSigningKey[HMACSHA256]
    ): IO[String] = {
      val stringToSign = concat(method, url, datetime)
      signToB64(stringToSign.utf8Bytes, key)
    }

    def verifyFromHeader(
        method: String,
        url: String,
        datetime: String,
        mac: String,
        key: MacSigningKey[HMACSHA256]
    ): Either[HmacAuthError, Instant] = {
      val stringToSign = concat(method, url, datetime)
      for {
        macBytes <- mac.b64Bytes.toRight(HmacAuthError.InvalidMacFormat)
        _        <- verifyEither(stringToSign.utf8Bytes, MAC[HMACSHA256](macBytes), key).leftMap(_ => HmacAuthError.BadMAC)
        instant <- Either
          .catchNonFatal(Instant.parse(datetime))
          .leftMap(_ => HmacAuthError.InvalidDatetime)
      } yield instant
    }
  }
}
