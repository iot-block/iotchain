package jbok.solidity.visitors

import jbok.solidity.visitors.ModifierList.Visibility
import jbok.solidity.visitors.TypeNameParameter.ParameterType

sealed trait ContractPart

object ContractPart {
  final case class FunctionDefinition(name: String,
                                      input: List[ParameterDefinition],
                                      outputs: List[ParameterDefinition],
                                      modifierList: ModifierList)
      extends ContractPart

  final case class StructDefinition(id: String, params: List[ParameterDefinition]) extends ContractPart

  final case class EnumDefinition(id: String, ids: Set[String]) extends ContractPart

  final case class StateVariableDefinition(id: String,
                                           inputs: List[ParameterType],
                                           outputs: List[ParameterType],
                                           visibility: Option[Visibility],
                                           isConstant: Boolean)
      extends ContractPart

  final case class NoImplementation() extends ContractPart
}
