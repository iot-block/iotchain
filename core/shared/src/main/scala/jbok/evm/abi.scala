package jbok.evm

import java.nio.charset.StandardCharsets

import io.circe.generic.auto._
import io.circe.parser._
import cats.implicits._
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import jbok.core.models.{Address, UInt256}
import scodec.bits.ByteVector
import jbok.crypto._

object abi {
  sealed trait AbiError
  case class InvalidType(reason: String)  extends AbiError
  case class InvalidParam(reason: String) extends AbiError
  case class InvalidValue(reason: String) extends AbiError

  case class Param(
      name: String, // the name of the parameter
      `type`: String, // the canonical type of the parameter (more below).
      components: Option[List[Param]],
  )

  case class ParamAttribute(isDynamic: Boolean, size: Option[Int])

  case class ParamWithAttr(
      name: String, // the name of the parameter
      `type`: String, // the canonical type of the parameter (more below).
      components: Option[List[ParamWithAttr]],
      attr: ParamAttribute
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

      def deocodeOutputs(result: ByteVector): Either[AbiError, Json] =
        outputs.map(decodeOutputs(_, result)).getOrElse(InvalidType("no outputs format.").asLeft)
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
            val fixedSize = "(\\[\\d+\\])"
            i.`type` match {
              case t if t.endsWith("[]") =>
                val subType   = t.substring(0, t.length - 2)
                val input     = i.copy(`type` = subType)
                val length    = p.asArray.get.size
                val inputList = List.fill(length)(i.copy(`type` = subType))
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
              case "tuple" =>
                if (i.components.nonEmpty) encodeInputs(i.components.get, p)
                else InvalidType("type is tuple but have no componects.").asLeft
              case _ => encodePrimaryType(i, p)
            }
        }

        if (results.forall(_.isRight)) {
          val rights = results.map(_.right.get)

          val isDynamic = rights.foldLeft(false) {
            case (pre, (isDynamic, _)) =>
              pre || isDynamic
          }
          val size = rights.foldLeft(0) {
            case (pre, (isDynamic, result)) =>
              if (isDynamic) {
                pre + 1
              } else {
                pre + result.size
              }
          }
          val (_, result) = rights.foldLeft((size, List.empty[ByteVector])) {
            case ((preSize, preBV), (isDynamic, result)) =>
              if (isDynamic) {
                (preSize + result.size, preBV :+ UInt256(preSize * 32).bytes)
              } else {
                (preSize, preBV ++ result)
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
          val b = if (bit.isEmpty) 256 else bit.toInt
          if (value.length == 32) {
            if (value.take(32 - b / 8).toArray.forall(_ == 0.toByte)) {
//              if (output.name.nonEmpty)
//                JsonObject((output.name, Json.fromBigInt(UInt256(value).toBigInt))).asJson.asRight
//              else
              Json.fromBigInt(UInt256(value).toBigInt).asRight
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
//              JsonObject((output.name, Json.fromBigInt(UInt256(value).toBigInt))).asJson.asRight
              Json.fromBigInt(UInt256(value).toBigInt).asRight
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
//            JsonObject((output.name, bool)).asJson.asRight
            bool.asRight
          } else {
            InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
          }
        } else {
          InvalidValue(s"${output.`type`} should have 32 bytes, but ${value.length}.").asLeft
        }
      case "address" =>
        if (value.length == 32) {
          if (value.take(12).toArray.forall(b => b == 0.toByte)) {
//            JsonObject((output.name, Json.fromString(Address(value).toString))).asJson.asRight
            Json.fromString(Address(value).toString).asRight
          } else {
            InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
          }
        } else {
          InvalidValue(s"${output.`type`} should have 32 bytes, but ${value.length}.").asLeft
        }
      case bytes(size) if size.toInt > 0 && size.toInt <= 32 =>
        if (value.length == 32) {
          if (value.takeRight(32 - size.toInt).toArray.forall(b => b == 0.toByte)) {
//            JsonObject((output.name, Json.fromString(s"0x${value.take(size.toInt).toHex}"))).asJson.asRight
            Json.fromString(s"0x${value.take(size.toInt).toHex}").asRight
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
//            JsonObject((output.name, Json.fromString(data))).asJson.asRight
            Json.fromString(data).asRight
          } else {
            InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
          }
        } else {
          InvalidValue(s"type: ${output.`type`}, value: ${value}").asLeft
        }
      case _ => InvalidType(s"type error: unkonwn type [${output.`type`}]").asLeft
    }
  }

  private def parseOutput(output: ParamWithAttr): Either[AbiError, ParamWithAttr] = {
    val tuple        = raw"tuple(\[\d*\])*".r
    val bytes        = raw"bytes(\[\d*\])*"
    val fixSizeArray = raw"[a-z0-9]+(\[\d*\])*".r
    output.`type` match {
      case t @ tuple(_) =>
        if (output.components.isEmpty) {
          InvalidType(s"tuple type must have components. ${output.`type`}").asLeft
        } else {
          val components = output.components.get
          if (t.contains("[]") || components.foldLeft(false)((z, p) => z || p.attr.isDynamic)) {
            output.copy(attr = ParamAttribute(true, None)).asRight
          } else {
            output
              .copy(attr = ParamAttribute(false, Some(components.foldLeft(0)((z, p) => z + p.attr.size.getOrElse(0)))))
              .asRight
          }
        }
      case t if t.contains("[]") || t.contains("string") || t.matches(bytes) =>
        output.copy(attr = ParamAttribute(true, None)).asRight
      case t @ fixSizeArray(_) =>
        val fixedSize = raw"\[(\d+)\]".r
        val totalSize =
          fixedSize.findAllIn(t).matchData.toList.map(_.group(1).toInt).foldLeft(1)((z, size) => z * size)
        output.copy(attr = ParamAttribute(false, Some(totalSize * 32))).asRight
      case _ =>
        output.copy(attr = ParamAttribute(false, Some(32))).asRight
    }
  }

  private def parseOutputs(outputs: List[Param]): Either[AbiError, List[ParamWithAttr]] = {
    val outputParsed: List[Either[AbiError, ParamWithAttr]] = outputs.map { output =>
      val tuple        = raw"tuple(\[\d*\])*".r
      val bytes        = raw"bytes(\[\d*\])*"
      val fixSizeArray = raw"[a-z0-9]+(\[\d*\])*".r
      output.`type` match {
        case t @ tuple(_) =>
          if (output.components.isEmpty) {
            InvalidType(s"tuple type must have components. ${output.`type`}").asLeft
          } else {
            val pwaList = parseOutputs(output.components.get)
            if (pwaList.isRight) {
              val pwas = pwaList.right.get
              if (t.contains("[]") || pwas.foldLeft(false)((z, pwa) => pwa.attr.isDynamic || z)) {
                ParamWithAttr(output.name, output.`type`, Some(pwas), ParamAttribute(true, None)).asRight
              } else {
                ParamWithAttr(output.name,
                              output.`type`,
                              Some(pwas),
                              ParamAttribute(false, Some(pwas.foldLeft(0)((z, pwa) => z + pwa.attr.size.get)))).asRight
              }
            } else {
              pwaList.left.get.asLeft
            }
          }
        case t if t.contains("[]") || t.contains("string") || t.matches(bytes) =>
          ParamWithAttr(output.name, output.`type`, None, ParamAttribute(true, None)).asRight
        case t @ fixSizeArray(_) =>
          val fixedSize = raw"\[(\d+)\]".r
          val totalSize =
            fixedSize.findAllIn(t).matchData.toList.map(_.group(1).toInt).foldLeft(1)((z, size) => z * size)
          ParamWithAttr(output.name, output.`type`, None, ParamAttribute(false, Some(totalSize * 32))).asRight
        case _ => ParamWithAttr(output.name, output.`type`, None, ParamAttribute(false, Some(32))).asRight
      }
    }

    if (outputParsed.forall(_.isRight)) {
      val result = outputParsed.map(_.right.get)
      result.asRight
    } else {
      outputParsed.find(_.isLeft).get.left.get.asLeft
    }
  }

  private def decodeOutputs_(outputs: List[ParamWithAttr], byteVector: ByteVector): Either[AbiError, Json] = {
    val (headSize, offsets) = outputs.foldLeft((0, List.empty[Int])) {
      case ((pre, offsets), output) =>
        if (output.attr.isDynamic)
          (pre + 32, offsets :+ UInt256(byteVector.slice(pre, pre + 32)).toInt)
        else
          (pre + output.attr.size.get, offsets)
    }
    val tailLength = (offsets :+ byteVector.length.toInt).sliding(2).toList.map {
      case a :: b :: Nil =>
        if (a % 32 != 0 || b % 32 != 0 || b < a)
          -1
        else
          b - a
      case _ => 0
    }

    if (tailLength.contains(-1)) {
      InvalidValue("type cannot parse value.").asLeft
    } else {
      val (_, _, values) = outputs.foldLeft((0, 0, List.empty[ByteVector])) {
        case ((pre, idx, byteVectors), output) =>
          if (output.attr.isDynamic) {
            val offset = UInt256(byteVector.slice(pre, pre + 32)).toInt
            (pre + 32, idx + 1, byteVectors :+ byteVector.slice(offset, offset + tailLength.get(idx).get))
          } else
            (pre + output.attr.size.get, idx, byteVectors :+ byteVector.slice(pre, pre + output.attr.size.get))
      }
      val eitherResults = outputs.zip(values).map {
        case (p, v) =>
          val fixedSize = "(\\[\\d+\\])"
          p.`type` match {
            case t if t.endsWith("[]") =>
              val (l, r)  = v.splitAt(32)
              val size    = UInt256(l).toInt
              val subType = t.substring(0, t.length - 2)
              val outputE = parseOutput(p.copy(name = "", `type` = subType))
              if (outputE.isRight) {
                val outputsList = List.fill(size)(outputE.right.get)
                decodeOutputs_(outputsList, r)
              } else {
                outputE.left.get.asLeft
              }
            case t if t.contains('[') && t.substring(t.lastIndexOf('[')).matches(fixedSize) =>
              if (!p.attr.isDynamic && p.attr.size.get == v.size) {
                val size        = (v.size / 32).toInt
                val subType     = t.substring(0, t.lastIndexOf('['))
                val outputsList = List.fill(size)(p.copy(`type` = subType))
                val outputE     = parseOutput(p.copy(name = "", `type` = subType))
                if (outputE.isRight) {
                  val outputsList = List.fill(size)(outputE.right.get)
                  decodeOutputs_(outputsList, v)
                } else {
                  outputE.left.get.asLeft
                }
              } else {
                InvalidType("fix size array must have same size element.").asLeft
              }
            case "tuple" =>
              if (p.components.nonEmpty)
                decodeOutputs_(p.components.get, v)
              else
                InvalidType("type is tuple but have no componects.").asLeft
            case _ => decodePrimaryType(Param(p.name, p.`type`, None), v)
          }
      }
      if (eitherResults.forall(_.isRight)) {
        val rights = eitherResults.map(_.right.get)
        rights.asJson.asRight
      } else {
        eitherResults.find(_.isLeft).get
      }
    }
  }

  def decodeOutputs(outputs: List[Param], byteVector: ByteVector): Either[AbiError, Json] =
    for {
      outputsParsed <- parseOutputs(outputs)
      json          <- decodeOutputs_(outputsParsed, byteVector)
    } yield json

}
