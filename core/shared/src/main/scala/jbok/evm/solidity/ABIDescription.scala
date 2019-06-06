package jbok.evm.solidity

import cats.implicits._
import io.circe.Json
import io.circe.generic.extras.ConfiguredJsonCodec
import scodec.bits.ByteVector
import jbok.crypto._
import io.circe.parser._
import io.circe.syntax._
import jbok.core.models.UInt256
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.JSExportAll

object ABIDescription {
  @JSExportAll
  @ConfiguredJsonCodec
  final case class ParameterType(solidityType: SolidityType, arrayList: List[Int]) {
    def typeString: String =
      solidityType.name + arrayList.map(size => if (size == 0) "[]" else s"[$size]").mkString

    lazy val isDynamic: Boolean = size.isEmpty

    lazy val size: Option[Int] =
      if (solidityType.isDynamic || arrayList.contains(0)) None else Some(32 * arrayList.product)
  }

  @JSExportAll
  @ConfiguredJsonCodec
  final case class ParameterDescription(name: Option[String], parameterType: ParameterType)

  @JSExportAll
  @ConfiguredJsonCodec
  final case class ContractDescription(name: String, methods: List[FunctionDescription])

  @JSExportAll
  @ConfiguredJsonCodec
  final case class FunctionDescription(
      name: String,
      inputs: List[ParameterDescription],
      outputs: List[ParameterDescription],
      stateMutability: String
  ) {
    lazy val methodID: ByteVector = ByteVector(methodWithOutReturn.getBytes).kec256.take(4)

    lazy val methodWithOutReturn =
      s"$name(${inputs.map(_.parameterType.typeString).mkString(",").trim})"

    def encode(params: String): Either[AbiError, ByteVector] =
      for {
        json    <- parse(params).leftMap(pa => InvalidParam(""))
        encoded <- FunctionDescription.encode(inputs.map(_.parameterType), json)
      } yield encoded.foldLeft(methodID)((xs, r) => xs ++ r)

    def decode(result: ByteVector): Either[AbiError, Json] =
      FunctionDescription.decode(outputs.map(_.parameterType), result)
  }

  object FunctionDescription {
    def encode(inputs: List[ParameterType], json: Json): Either[AbiError, List[ByteVector]] =
      for {
        params <- if (json.asArray.map(_.toList).isEmpty)
          InvalidParam("").asLeft
        else Right(json.asArray.getOrElse(List.empty[Json]))
        resultEither <- if (inputs.size != params.size) {
          InvalidParam("").asLeft
        } else {
          for {
            results <- inputs
              .zip(params)
              .map {
                case (i, p) =>
                  i.arrayList.lastOption match {
                    case Some(0) =>
                      val subType = i.copy(arrayList = i.arrayList.take(i.arrayList.size - 1))
                      p.asArray
                        .map { vector =>
                          val length    = vector.size
                          val inputList = List.fill(length)(subType)
                          for {
                            results <- encode(inputList, p)
                            len = UInt256(length).bytes
                          } yield len :: results
                        }
                        .getOrElse(Left(InvalidParam("")))

                    case Some(size) if size > 0 =>
                      val subType = i.copy(arrayList = i.arrayList.take(i.arrayList.size - 1))
                      p.asArray
                        .map { vector =>
                          val length    = vector.size
                          val inputList = List.fill(length)(subType)
                          if (length != size) {
                            InvalidParam("").asLeft
                          } else {
                            encode(inputList, p)
                          }
                        }
                        .getOrElse(Left(InvalidParam("")))

                    case _ =>
                      i.solidityType.encode(p)
                  }
              }
              .foldRight(Right(Nil): Either[AbiError, List[List[ByteVector]]]) { (elem, acc) =>
                acc.right.flatMap(list => elem.right.map(_ :: list))
              }
          } yield {
            val parameterAndResult = inputs.zip(results)
            val staticSize = parameterAndResult.foldLeft(0) {
              case (preSize, (pt, result)) =>
                if (pt.isDynamic) {
                  preSize + 1
                } else {
                  preSize + result.size
                }
            }
            val (staticPart, dynamicPart) =
              parameterAndResult.foldLeft((List.empty[ByteVector], List.empty[ByteVector])) {
                case ((sp, dp), (pt, result)) =>
                  if (pt.isDynamic) {
                    (sp :+ UInt256((staticSize + dp.size) * 32).bytes, dp ++ result)
                  } else {
                    (sp ++ result, dp)
                  }
              }

            staticPart ++ dynamicPart
          }
        }
      } yield resultEither

    def decode(outputs: List[ParameterType], byteVector: ByteVector): Either[AbiError, Json] = {
      val (_, offsets) = outputs.foldLeft((0, List.empty[Int])) {
        case ((pre, offsets), output) =>
          output.size
            .map(s => (pre + s, offsets))
            .getOrElse((pre + 32, offsets :+ UInt256(byteVector.slice(pre, pre + 32)).toInt))
      }

      val daynamicSizeList = (offsets :+ byteVector.length.toInt).sliding(2).toList.map {
        case a :: b :: Nil => if (a % 32 != 0 || b % 32 != 0 || b < a) -1 else b - a
        case _             => 0
      }

      if (daynamicSizeList.contains(-1)) {
        InvalidValue("type cannot parse value.").asLeft
      } else {
        val (_, _, values) = outputs.foldLeft((0, 0, List.empty[ByteVector])) {
          case ((pre, idx, byteVectors), output) =>
            output.size.map(s => (pre + s, idx, byteVectors :+ byteVector.slice(pre, pre + s))).getOrElse {
              val offset = UInt256(byteVector.slice(pre, pre + 32)).toInt
              (pre + 32, idx + 1, byteVectors :+ byteVector.slice(offset, offset + daynamicSizeList.get(idx).getOrElse(0)))
            }
        }
        val eitherResults = outputs.zip(values).map {
          case (p, v) =>
            p.arrayList.lastOption match {
              case Some(0) =>
                val (l, r)      = v.splitAt(32)
                val length      = UInt256(l).toInt
                val subType     = p.copy(arrayList = p.arrayList.take(p.arrayList.size - 1))
                val outputsList = List.fill(length)(subType)
                decode(outputsList, r)

              case Some(size) if size > 0 =>
                p.size
                  .filter(_ == v.size)
                  .map { size =>
                    val length      = (v.size / 32).toInt
                    val subType     = p.copy(arrayList = p.arrayList.take(p.arrayList.size - 1))
                    val outputsList = List.fill(length)(subType)
                    decode(outputsList, v)
                  }
                  .getOrElse(InvalidType("fix size array must have same size element.").asLeft)
              case _ =>
                p.solidityType.decode(v)
            }
        }

        eitherResults
          .foldRight(Right(Nil): Either[AbiError, List[Json]]) { (elem, acc) =>
            acc.right.flatMap(list => elem.right.map(_ :: list))
          }
          .map(_.asJson)
      }
    }
  }

}
