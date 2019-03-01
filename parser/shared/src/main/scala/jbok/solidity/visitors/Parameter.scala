package jbok.solidity.visitors

import io.circe.generic.JsonCodec
import jbok.solidity.SolidityType
import jbok.solidity.visitors.TypeNameParameter.{MappingType, ParameterType}

import scala.scalajs.js.annotation.JSExportAll

sealed trait TypeNameParameter

final case class ParameterDefinition(name: Option[String], typeNameParameter: TypeNameParameter) {
  lazy val canBuildABI: Boolean = typeNameParameter match {
    case m @ MappingType(_, _)    => false
    case pt @ ParameterType(_, _) => true
  }
}

object TypeNameParameter {
  case object Undone extends TypeNameParameter
  @JSExportAll
  @JsonCodec
  final case class ParameterType(solidityType: SolidityType, arrayList: List[Int]) extends TypeNameParameter {
    def typeString: String =
      solidityType.name + arrayList.map(size => if (size == 0) "[]" else s"[$size]").mkString

    lazy val isDynamic: Boolean = size.isEmpty

    lazy val size: Option[Int] =
      if (solidityType.isDynamic || arrayList.contains(0)) None else Some(32 * arrayList.product)
  }

  final case class MappingType(inputs: List[ParameterType], outputs: List[ParameterType]) extends TypeNameParameter

}
