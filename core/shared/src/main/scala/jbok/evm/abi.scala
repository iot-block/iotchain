package jbok.evm

import io.circe.generic.auto._
import io.circe.parser._
import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.syntax._

object abi {
  case class Param0(
      name: String, // the name of the parameter
      `type`: String // the canonical type of the parameter (more below).
  )

  case class Param(
      name: String, // the name of the parameter
      `type`: String, // the canonical type of the parameter (more below).
      components: Option[List[Param0]]
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
    ) extends Description

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
}
