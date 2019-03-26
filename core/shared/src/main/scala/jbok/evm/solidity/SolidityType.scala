package jbok.evm.solidity

import java.nio.charset.StandardCharsets

import cats.implicits._
import io.circe.Json
import io.circe.generic.JsonCodec
import jbok.core.models.{Address, UInt256}
import jbok.crypto._
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.JSExportAll

trait AbiError

final case class InvalidParam(reason: String) extends AbiError
final case class InvalidValue(reason: String) extends AbiError
final case class InvalidType(reason: String)  extends AbiError

@JSExportAll
@JsonCodec
sealed abstract class SolidityType {
  def encode(json: Json): Either[AbiError, List[ByteVector]]

  def decode(bv: ByteVector): Either[AbiError, Json]

  val isDynamic: Boolean = false

  val name: String
}

final case class IntType(n: Int) extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] =
    json.asNumber.flatMap(_.toBigInt).map(UInt256(_).bytes) match {
      case Some(result) => List.apply(result).asRight
      case None         => InvalidParam(s"${json.toString} is not int type.").asLeft
    }

  override def decode(value: ByteVector): Either[AbiError, Json] =
    if (value.length == 32) {
      if (value.take(32 - n / 8).toArray.forall(b => b == 0.toByte || b == 255.toByte)) {
        Json.fromBigInt(UInt256(value).toBigInt).asRight
      } else { InvalidValue(s"type: int$n, value: $value").asLeft }
    } else { InvalidValue(s"int$n should have 32 bytes, but ${value.length}.").asLeft }

  override val name: String = s"int${n}"
}

final case class UIntType(n: Int) extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] =
    json.asNumber.flatMap(_.toBigInt).filter(_ >= 0).map(UInt256(_).bytes) match {
      case Some(result) => List.apply(result).asRight
      case None         => InvalidParam(s"${json.toString} is not uint type.").asLeft
    }

  override def decode(value: ByteVector): Either[AbiError, Json] =
    if (value.length == 32) {
      if (value.take(32 - n / 8).toArray.forall(_ == 0.toByte)) {
        Json.fromBigInt(UInt256(value).toBigInt).asRight
      } else { InvalidValue(s"type: uint$n, value: $value").asLeft }
    } else { InvalidValue(s"uint$n should have 32 bytes, but ${value.length}.").asLeft }

  override val name: String = s"uint${n}"
}

final case class AddressType() extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] =
    json.asString
      .flatMap {
        case p if p.length == 40                       => ByteVector.fromHex(p)
        case p if p.length == 42 && p.startsWith("0x") => ByteVector.fromHex(p.substring(2))
        case _                                         => None
      }
      .map(UInt256(_).bytes) match {
      case Some(result) => List.apply(result).asRight
      case None         => InvalidParam(s"${json.toString} is not address type.").asLeft
    }

  override def decode(value: ByteVector): Either[AbiError, Json] =
    if (value.length == 32) {
      if (value.take(12).toArray.forall(b => b == 0.toByte)) {
        Json.fromString(Address(value).toString).asRight
      } else { InvalidValue(s"type: address, value: $value").asLeft }
    } else { InvalidValue(s"address should have 32 bytes, but ${value.length}.").asLeft }

  override val name: String = "address"
}

final case class FixedType(m: Int, n: Int) extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] = ???

  override def decode(bv: ByteVector): Either[AbiError, Json] = ???

  override val name: String = ???
}

final case class UnFixedType(m: Int, n: Int) extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] = ???

  override def decode(bv: ByteVector): Either[AbiError, Json] = ???

  override val name: String = ???
}

final case class BoolType() extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] =
    json.asBoolean.map(b => if (b) 1 else 0).map(UInt256(_).bytes) match {
      case Some(result) => List.apply(result).asRight
      case None         => InvalidParam(s"${json.toString} is not boolean type.").asLeft
    }

  override def decode(value: ByteVector): Either[AbiError, Json] =
    if (value.length == 32) {
      if (value.take(31).toArray.forall(_ == 0.toByte) && (value.last == 0.toByte || value.last == 1.toByte)) {
        val bool = if (value.last == 1.toByte) Json.True else Json.False
        bool.asRight
      } else { InvalidValue(s"type: bool, value: $value").asLeft }
    } else { InvalidValue(s"bool should have 32 bytes, but ${value.length}.").asLeft }

  override val name: String = "bool"
}

final case class BytesType() extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] =
    (for {
      value <- json.asString
      if value.length % 2 == 0
      r <- if (value.length == 0)
        Some(ByteVector.empty.padTo(32))
      else {
        val byteLength       = value.length / 2
        val byteVectorLength = (byteLength - 1) / 32 + 1
        val bytesOpt         = ByteVector.fromHex(value)
        bytesOpt.map { bytes =>
          val len = UInt256(bytes.length).bytes
          len ++ bytes.padRight(byteVectorLength * 32)
        }
      }
    } yield r) match {
      case Some(result) => SolidityType.splitToFixSizeList(result).asRight
      case None         => InvalidParam(s"${json.toString} is not bytes, should have even length and be hex.").asLeft
    }

  override def decode(value: ByteVector): Either[AbiError, Json] =
    if (value.length != 0 && value.length % 32 == 0) {
      val (l, r) = value.splitAt(32)
      val size   = UInt256(l).toBigInt.toLong
      if (size <= r.length && r.takeRight(r.length - size).toArray.forall(_ == 0.toByte)) {
        val data = s"0x${r.take(size).toHex}"
        Json.fromString(data).asRight
      } else { InvalidValue(s"type: bytes, cannot get string prefix length, value: $value").asLeft }
    } else { InvalidValue(s"type: bytes, value: $value should (length != 0 && length % 32 = 0)").asLeft }

  override val isDynamic: Boolean = true

  override val name: String = "bytes"
}

final case class BytesNType(n: Int) extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] =
    json.asString.filter(_.length == n * 2).flatMap(ByteVector.fromHex(_)).map(_.padTo(32)) match {
      case Some(result) => List.apply(result).asRight
      case None         => InvalidParam(s"${json.toString} is not bytes$n, should have ${n * 2} length and be hex.").asLeft
    }

  override def decode(value: ByteVector): Either[AbiError, Json] =
    if (value.length == 32) {
      if (value.takeRight(32 - n).toArray.forall(b => b == 0.toByte)) {
        Json.fromString(s"0x${value.take(n).toHex}").asRight
      } else { InvalidValue(s"type: bytes$n, value: $value").asLeft }
    } else { InvalidValue(s"bytes$n should have 32 bytes, but ${value.length}.").asLeft }

  override val name: String = s"bytes$n"
}

final case class StringType() extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] =
    (for {
      value <- json.asString
      r = if (value.length == 0) {
        ByteVector.empty.padTo(32)
      } else {
        val bytes            = value.utf8bytes
        val byteVectorLength = (bytes.length - 1) / 32 + 1
        val len              = UInt256(bytes.length).bytes
        len ++ bytes.padRight(byteVectorLength * 32)
      }
    } yield r) match {
      case Some(result) => SolidityType.splitToFixSizeList(result).asRight
      case None         => InvalidParam(s"${json.toString} is not string").asLeft
    }

  override def decode(value: ByteVector): Either[AbiError, Json] =
    if (value.length != 0 && value.length % 32 == 0) {
      val (l, r) = value.splitAt(32)
      val size   = UInt256(l).toBigInt.toLong
      if (size <= r.length && r.takeRight(r.length - size).toArray.forall(_ == 0.toByte)) {
        val codec = scodec.codecs.string(StandardCharsets.UTF_8)
        codec.decode(r.take(size).bits).toOption.map(_.value) match {
          case Some(data) => Json.fromString(data).asRight
          case None       => InvalidValue(s"type: string, invalid character in value: $value").asLeft
        }
      } else { InvalidValue(s"type: string, cannot get string prefix length, value: $value").asLeft }
    } else { InvalidValue(s"type: string, value: $value should (length != 0 && length % 32 = 0)").asLeft }

  override val isDynamic: Boolean = true

  override val name: String = "string"
}

final case class InvalidSolidityType() extends SolidityType {
  override def encode(json: Json): Either[AbiError, List[ByteVector]] = InvalidType("invalid type").asLeft

  override def decode(bv: ByteVector): Either[AbiError, Json] = InvalidType("invalid type").asLeft

  override val name: String = "invalid"
}

object SolidityType {
  def getType(`type`: String): Either[AbiError, SolidityType] = {
    val uint   = "uint([0-9]*)".r
    val int    = "int([0-9]*)".r
    val bytes  = "bytes([0-9]+)".r
    val fixed  = "fixed([0-9]+)x([0-9]+)".r
    val ufixed = "ufixed([0-9]+)x([0-9]+)".r

    def numericType(bit: String): Either[AbiError, Int] =
      if (bit.nonEmpty && (bit.toInt % 8 != 0 || bit.toInt > 256 || bit.toInt == 0)) {
        InvalidType(s"${`type`} with specific bits must satisfy [ num % 8 == 0 ].").asLeft
      } else {
        (if (bit.isEmpty) 256 else bit.toInt).asRight
      }

    `type` match {
      case uint(bit)                                         => numericType(bit).map(UIntType)
      case int(bit)                                          => numericType(bit).map(IntType)
      case fixed(_, _)                                       => InvalidType("fixed type not implement.").asLeft
      case ufixed(_, _)                                      => InvalidType("ufixed type not implement").asLeft
      case "bool"                                            => BoolType().asRight
      case "address"                                         => AddressType().asRight
      case bytes(size) if size.toInt > 0 && size.toInt <= 32 => BytesNType(size.toInt).asRight
      case "bytes"                                           => BytesType().asRight
      case "string"                                          => StringType().asRight
      case _                                                 => InvalidType(s"type error: unkonwn type [${`type`}]").asLeft
    }
  }

  def splitToFixSizeList(byteVector: ByteVector): List[ByteVector] = {
    val (l, r) = byteVector.splitAt(32)
    if (r.nonEmpty) {
      List.apply(l) ++ splitToFixSizeList(r)
    } else {
      List.apply(l)
    }
  }
}
