package jbok.sdk

import jbok.solidity.ABIDescription
import jbok.solidity.ABIDescription.ContractDescription
import scodec.bits.ByteVector

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
