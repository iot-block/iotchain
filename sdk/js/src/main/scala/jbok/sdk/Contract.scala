package jbok.sdk

import fastparse.Parsed
import io.circe.generic.{JsonCodec => JsonCodecAnnotion}
import jbok.evm.solidity.SolidityParser
import jbok.evm.solidity.ABIDescription
import jbok.evm.solidity.ABIDescription.ContractDescription
import scodec.bits.ByteVector
import _root_.io.circe.syntax._
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.UndefOr

@JSExportTopLevel("Contracts")
@JSExportAll
@JsonCodecAnnotion
final case class Contracts(contractDescriptions: List[ContractDescription]) {
  private lazy val contracts = contractDescriptions.map(cd => cd.name -> cd).toMap

  private def methods(name: String): Option[Map[String, ABIDescription.FunctionDescription]] =
    contracts.get(name).map(_.methods.map(method => method.name -> method).toMap)

  def encode(contractName: String, method: String, param: String): UndefOr[String] =
    (for {
      function <- methods(contractName).flatMap(_.get(method))
      returns = function.encode(param) match {
        case Right(value) => value.toHex
        case Left(e)      => e.toString
      }
    } yield returns).orUndefined

  def decode(contractName: String, method: String, param: String): UndefOr[String] =
    (for {
      function <- methods(contractName).flatMap(_.get(method))
      result   <- ByteVector.fromHex(param)
      returns = function.decode(result) match {
        case Right(value) => value.noSpaces
        case Left(e)      => e.toString
      }
    } yield returns).orUndefined
}

@JsonCodecAnnotion
final case class ParsedResult(contracts: Option[Contracts], error: String)

@JSExportTopLevel("ContractParser")
@JSExportAll
object ContractParser {
  def parse(code: String): String = {
    val result = SolidityParser.parseSource(code)

    if (result.isSuccess) {
      ParsedResult(Some(Contracts(result.get.value.ABI)), "").asJson.noSpaces
    } else {
      ParsedResult(None, result.asInstanceOf[Parsed.Failure].trace().longAggregateMsg).asJson.noSpaces
    }
  }
}
