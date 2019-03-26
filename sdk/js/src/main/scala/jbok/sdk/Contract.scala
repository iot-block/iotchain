package jbok.sdk

import jbok.evm.solidity.SolidityParser
import jbok.evm.solidity.ABIDescription
import jbok.evm.solidity.ABIDescription.ContractDescription
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.UndefOr

@JSExportTopLevel("Contract")
@JSExportAll
final case class Contract(contractDescription: ContractDescription) {
  private def methods: Map[String, ABIDescription.FunctionDescription] =
    contractDescription.methods.map(method => method.name -> method).toMap

  def encode(method: String, param: String): UndefOr[String] =
    (for {
      function <- methods.get(method)
      returns = function.encode(param) match {
        case Right(value) => value.toHex
        case Left(e)      => e.toString
      }
    } yield returns).orUndefined

  def decode(method: String, param: String): UndefOr[String] =
    (for {
      function <- methods.get(method)
      result   <- ByteVector.fromHex(param)
      returns = function.decode(result) match {
        case Right(value) => value.noSpaces
        case Left(e)      => e.toString
      }
    } yield returns).orUndefined
}

@JSExportTopLevel("ContractParser")
@JSExportAll
object ContractParser {
  def parse(code: String): UndefOr[Contract] = {
    val result = SolidityParser.parseContract(code)

    (if (result.isSuccess) {
       Some(Contract(result.get.value.toABI))
     } else {
       None
     }).orUndefined
  }
}
