package jbok.evm

import java.nio.charset.StandardCharsets

import io.circe.generic.auto._
import io.circe.parser._
import cats.implicits._
import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.syntax._
import jbok.core.models.{Address, UInt256}
import scodec.bits.ByteVector
import jbok.crypto._

object abi {

  case class Param(
      name: String, // the name of the parameter
      `type`: String, // the canonical type of the parameter (more below).
      components: Option[List[Param]],
  )

  sealed trait Description

  object Description {

    case class Function(
        name: Option[String], // the name of the function
        inputs: List[Param],
        outputs: Option[List[Param]],
        stateMutability: String, // "pure", "view", "nonpayable", "payable",
        `type`: Option[String] = Some("function"), // "function", "constructor" or "fallback"
        payable: Option[Boolean] = Some(false), // true if function accepts Ether, false otherwise;
        constant: Option[Boolean] = Some(false) // true if function is either pure or view, false otherwise.
    ) extends Description {
      private def inputTypes(inputs: Param): String =
        if (inputs.`type`.startsWith("tuple")) {
          val t     = inputs.components.map(p => s"(${p.map(inputTypes).mkString(",").trim})").getOrElse("")
          val tuple = raw"tuple([\[\d*\]]*)".r
          inputs.`type` match {
            case tuple(end) => s"$t$end"
            case _          => inputs.`type`
          }
        } else {
          inputs.`type`
        }

      lazy val methodID = ByteVector(methodWithOutReturn.getBytes.kec256.take(4))

      lazy val methodWithOutReturn = s"${name.getOrElse("")}(${inputs.map(inputTypes).mkString(",").trim})"

      def getByteCode(params: String): Either[AbiError, ByteVector] =
        for {
          json                 <- parse(params).leftMap(pa => InvalidParam(""))
          (isDynamic, encoded) <- encodeInputs(inputs, json)
        } yield encoded.foldLeft(methodID)((xs, r) => xs ++ r)

      def deocodeOutputs(result: ByteVector): String = ???
    }

    case class Event(
        `type`: String,
        name: String, // the name of the event
        inputs: List[Param],
        anonymous: Boolean
    ) extends Description

    implicit val encode: Encoder[Description] = Encoder.instance {
      case function: Function => function.asJson
      case event: Event       => event.asJson
    }

    implicit val decode: Decoder[Description] =
      List[Decoder[Description]](
        Decoder[Function].widen,
        Decoder[Event].widen
      ).reduceLeft(_ or _)
  }

  def parseFunction(str: String): Either[Throwable, Description.Function] =
    decode[Description.Function](str)

  def parseEvent(str: String): Either[Throwable, Description.Event] =
    decode[Description.Event](str)

  def parseDescription(str: String): Either[Throwable, Description] =
    decode[Description](str)

  def parseContract(str: String): Either[Throwable, List[Description]] =
    decode[List[Description]](str)

  def encodePrimaryType(input: Param, param: Json): Either[AbiError, (Boolean, List[ByteVector])] = {
    val uint    = "uint([0-9]*)".r
    val int     = "int([0-9]*)".r
    val bytes   = "bytes([0-9]+)".r
    val fixed   = "fixed([0-9]+)x([0-9]+)".r
    val unfixed = "unfixed([0-9]+)x([0-9]+)".r

    input.`type` match {
      case uint(bit) =>
        if (bit.nonEmpty && bit.toInt % 8 != 0) {
          InvalidType(s"${input.`type`} with specific bits must satisfy [ num % 8 == 0 ].").asLeft
        } else {
          val b = if (bit.isEmpty) 256 else bit.toInt
          param.asNumber.flatMap(_.toBigInt).filter(_ >= 0).map(UInt256(_).bytes) match {
            case Some(result) => (false, List.apply(result)).asRight
            case None         => InvalidParam(s"${param.toString} is not uint type.").asLeft
          }
        }
      case int(bit) =>
        if (bit.nonEmpty && bit.toInt % 8 != 0) {
          InvalidType(s"${input.`type`} with specific bits must satisfy [ num % 8 == 0 ].").asLeft
        } else {
          val b = if (bit.isEmpty) 256 else bit.toInt
          param.asNumber.flatMap(_.toBigInt).map(UInt256(_).bytes) match {
            case Some(result) => (false, List.apply(result)).asRight
            case None         => InvalidParam(s"${param.toString} is not int type.").asLeft
          }
        }
      case fixed(m, n) =>
        val M = m.toInt
        val N = n.toInt
        if (8 <= M && M <= 256 && M % 8 == 0 && 0 < N && N <= 80) {
          for {
            number <- param.asNumber
            parts      = number.toString.split('.').toList
            integral   = parts.head.toInt
            fractional = parts.last.toInt
          } yield ()
        } else {
          InvalidType(s"fixed<M>x<N>: ${input.`type`} must satisfy [8 <= M <= 256, M % 8 ==0, 0 < N <= 80]").asLeft
        }
        InvalidType(s"${input.`type`} unsupport.").asLeft
      case unfixed(a, b) =>
        // in doc https://solidity.readthedocs.io/en/latest/abi-spec.html#types
        InvalidType(s"${input.`type`} unsupport.").asLeft
      case "bool" =>
        param.asBoolean.map(b => if (b) 1 else 0).map(UInt256(_).bytes) match {
          case Some(result) => (false, List.apply(result)).asRight
          case None         => InvalidParam(s"${param.toString} is not boolean type.").asLeft
        }
      case "address" =>
        param.asString.filter(_.length == 40).flatMap(ByteVector.fromHex(_)).map(UInt256(_).bytes) match {
          case Some(result) => (false, List.apply(result)).asRight
          case None         => InvalidParam(s"${param.toString} is not address type.").asLeft
        }
      case bytes(size) if size.toInt > 0 && size.toInt <= 32 =>
        param.asString.filter(_.length == size.toInt * 2).flatMap(ByteVector.fromHex(_)).map(_.padTo(32)) match {
          case Some(result) => (false, List.apply(result)).asRight
          case None =>
            InvalidParam(s"${param.toString} is not ${input.`type`}, should have ${size.toInt * 2} length and be hex.").asLeft
        }
      case "bytes" =>
        (for {
          value <- param.asString
          if value.length % 2 == 0
          r = if (value.length == 0)
            ByteVector.empty.padTo(32)
          else {
            val byteLength       = value.length / 2
            val byteVectorLength = (byteLength - 1) / 32 + 1
            val bytes            = ByteVector.fromValidHex(value)
            val len              = UInt256(bytes.length).bytes
            len ++ bytes.padRight(byteVectorLength * 32)
          }
        } yield r) match {
          case Some(result) => (true, splitToFixSizeList(result)).asRight
          case None         => InvalidParam(s"${param.toString} is not bytes, should have even length and be hex.").asLeft
        }
      case "string" =>
        (for {
          value <- param.asString
          r = if (value.length == 0) {
            ByteVector.empty.padTo(32)
          } else {
            val bytes            = value.utf8bytes
            val byteVectorLength = (bytes.length - 1) / 32 + 1
            val len              = UInt256(bytes.length).bytes
            len ++ bytes.padRight(byteVectorLength * 32)
          }
        } yield r) match {
          case Some(result) => (true, splitToFixSizeList(result)).asRight
          case None         => InvalidParam(s"${param.toString} is not string").asLeft
        }
      case _ => InvalidType(s"type error: unkonwn type [${input.`type`}]").asLeft
    }
  }

  def encodeInputs(inputs: List[Param], param: Json): Either[AbiError, (Boolean, List[ByteVector])] =
    (for {
      params <- param.asArray.map(_.toList)
      resultEither: Either[AbiError, (Boolean, List[ByteVector])] = if (inputs.size != params.size) {
        InvalidParam("").asLeft
      } else {
        val results = inputs.zip(params).map {
          case (i, p) =>
            val fixedArray = "\\[([0-9]+)\\]\\$".r
            val fixedSize  = "(\\[\\d+\\])"
            i.`type` match {
              case t if t.endsWith("[]") =>
                val subType   = t.substring(0, t.length - 2)
                val input     = i.copy(`type` = subType)
                val length    = p.asArray.get.size
                val inputList = List.fill(length)(input)
                for {
                  (b, r) <- encodeInputs(inputList, p)
                  len = UInt256(length).bytes
                } yield (true, len :: r)
              case t if t.contains('[') && t.substring(t.lastIndexOf('[')).matches(fixedSize) =>
                val size      = t.substring(t.lastIndexOf('[') + 1, t.size - 1).toInt
                val subType   = t.substring(0, t.lastIndexOf('['))
                val input     = i.copy(`type` = subType)
                val inputList = List.fill(p.asArray.get.size)(input)
                encodeInputs(inputList, p)
              case "tuple" => encodeInputs(i.components.get, p)
              case _       => encodePrimaryType(i, p)
            }
        }

        if (results.forall(_.isRight)) {
          val rights = results.map(_.right.get)

          val isDynamic = rights.foldLeft(false) {
            case (pre, (isDynamic, _)) =>
              pre || isDynamic
          }
          var size = rights.foldLeft(0) {
            case (pre, (isDynamic, result)) =>
              if (isDynamic) {
                pre + 1
              } else {
                pre + result.size
              }
          }
          val result = rights.foldLeft(List.empty[ByteVector]) {
            case (pre, (isDynamic, result)) =>
              if (isDynamic) {
                val r = pre :+ UInt256(size * 32).bytes
                size += result.size
                r
              } else {
                pre ++ result
              }
          }

          val result2 = rights.foldLeft(result) {
            case (pre, (isDynamic, result)) =>
              if (isDynamic) {
                pre ++ result
              } else {
                pre
              }
          }
          (isDynamic, result2).asRight
        } else {
          results.find(_.isLeft).get
        }
      }
    } yield resultEither).get

  private def splitToFixSizeList(byteVector: ByteVector): List[ByteVector] = {
    val (l, r) = byteVector.splitAt(32)
    if (r.nonEmpty) {
      List.apply(l) ++ splitToFixSizeList(r)
    } else {
      List.apply(l)
    }
  }

  def decodePrimaryType(output: Param, value: ByteVector): Either[AbiError, Json] = {
    val uint    = "uint([0-9]*)".r
    val int     = "int([0-9]*)".r
    val bytes   = "bytes([0-9]+)".r
    val fixed   = "fixed([0-9]+)x([0-9]+)".r
    val unfixed = "unfixed([0-9]+)x([0-9]+)".r

    output.`type` match {
      case uint(bit) =>
        if (bit.nonEmpty && (bit.toInt % 8 != 0 || bit.toInt > 256 || bit.toInt == 0)) {
          InvalidType(s"${output.`type`} with specific bits must satisfy [ num % 8 == 0 ].").asLeft
        } else {
          if (value.length == 32) {
            if (value.take(32 - bit.toInt / 8).toArray.forall(_ == 0.toByte)) {
              JsonObject((output.name, Json.fromBigInt(UInt256(value).toBigInt))).asJson.asRight
            } else {
              InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
            }
          } else {
            InvalidValue(s"${output.`type`} should have 32 bytes, but ${value.length}.").asLeft
          }
        }
      case int(bit) =>
        if (bit.nonEmpty && bit.toInt % 8 != 0) {
          InvalidType(s"${output.`type`} with specific bits must satisfy [ num % 8 == 0 ].").asLeft
        } else {
          if (value.length == 32) {
            if (value.take(32 - bit.toInt / 8).toArray.forall(b => b == 0.toByte || b == 255.toByte)) {
              JsonObject((output.name, Json.fromBigInt(UInt256(value).toBigInt))).asJson.asRight
            } else {
              InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
            }
          } else {
            InvalidValue(s"${output.`type`} should have 32 bytes, but ${value.length}.").asLeft
          }
        }
      case fixed(m, n) =>
        InvalidType(s"${output.`type`} unsupport.").asLeft
      case unfixed(a, b) =>
        // in doc https://solidity.readthedocs.io/en/latest/abi-spec.html#types
        InvalidType(s"${output.`type`} unsupport.").asLeft
      case "bool" =>
        if (value.length == 32) {
          if (value.take(31).toArray.forall(_ == 0.toByte) && (value.last == 0.toByte || value.last == 1.toByte)) {
            val bool = if (value.last == 1.toByte) Json.True else Json.False
            JsonObject((output.name, bool)).asJson.asRight
          } else {
            InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
          }
        } else {
          InvalidValue(s"${output.`type`} should have 32 bytes, but ${value.length}.").asLeft
        }
      case "address" =>
        if (value.length == 32) {
          if (value.take(12).toArray.forall(b => b == 0.toByte)) {
            JsonObject((output.name, Json.fromString(Address(value).toString))).asJson.asRight
          } else {
            InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
          }
        } else {
          InvalidValue(s"${output.`type`} should have 32 bytes, but ${value.length}.").asLeft
        }
      case bytes(size) if size.toInt > 0 && size.toInt <= 32 =>
        if (value.length == 32) {
          if (value.takeRight(32 - size.toInt).toArray.forall(b => b == 0.toByte)) {
            JsonObject((output.name, Json.fromString(s"0x${value.take(size.toInt).toHex}"))).asJson.asRight
          } else {
            InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
          }
        } else {
          InvalidValue(s"${output.`type`} should have 32 bytes, but ${value.length}.").asLeft
        }
      case "bytes" | "string" =>
        if (value.length != 0 && value.length % 32 == 0) {
          val (l, r) = value.splitAt(32)
          val size   = UInt256(l).toBigInt.toLong
          if (size < r.length && r.takeRight(r.length - size).toArray.forall(_ == 0.toByte)) {
            val data = if (output.`type` == "bytes") {
              s"0x${r.take(size).toHex}"
            } else {
              val codec = scodec.codecs.string(StandardCharsets.UTF_8)
              codec.decode(r.take(size).bits).require.value
            }
            JsonObject((output.name, Json.fromString(data))).asJson.asRight
          } else {
            InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
          }
        } else {
          InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
        }
      case _ => InvalidType(s"type error: unkonwn type [${output.`type`}]").asLeft
    }
  }

  def decodeOutputs(outputs: List[Param], byteVector: ByteVector): Either[AbiError, Json] =
    ???

}
