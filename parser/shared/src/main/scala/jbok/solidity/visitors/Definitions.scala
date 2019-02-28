package jbok.solidity.visitors

import jbok.solidity.ABIDescription.{ContractDescription, FunctionDescription, ParameterDescription}

final case class ContractDefinition(`type`: String,
                                    id: String,
                                    functions: Map[String, FunctionDefinition],
                                    stateVariable: Map[String, StateVariableDefinition],
                                    structDefinitions: Map[String, StructDefinition],
                                    enumDefinitions: Map[String, EnumDefinition]) {
  def toABI: ContractDescription = {
    val functionPart = functions.values
      .filter(
        f =>
          (f.modifierList.visibility.isEmpty || f.modifierList.visibility.contains(Public)) && (f.input.forall(
            _.canBuildABI) && f.outputs.forall(_.canBuildABI)))
      .map(f =>
        FunctionDescription(
          f.name,
          f.input.map { p =>
            ParameterDescription(p.name, p.typeNameParameter.asInstanceOf[ParameterType])
          },
          f.outputs.map { p =>
            ParameterDescription(p.name, p.typeNameParameter.asInstanceOf[ParameterType])
          },
          if (f.modifierList.stateMutability.contains(View) || f.modifierList.stateMutability.contains(Pure)) "view"
          else ""
      ))
    val variablePart =
      stateVariable.values
        .filter(v => v.visibility.isEmpty || v.visibility.contains(Public))
        .map(
          v =>
            FunctionDescription(v.id,
                                v.inputs.map(ParameterDescription(None, _)),
                                v.outputs.map(ParameterDescription(None, _)),
                                "view"))
    val all = functionPart ++ variablePart

    ContractDescription(id, all.toList)
  }
}

object ContractDefinition {
  def empty = ContractDefinition("", "", Map.empty, Map.empty, Map.empty, Map.empty)
}

sealed trait ContractPart

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

final case class ParameterDefinition(name: Option[String], typeNameParameter: TypeNameParameter) {
  lazy val canBuildABI: Boolean = typeNameParameter match {
    case m @ MappingType(_, _)    => false
    case pt @ ParameterType(_, _) => true
  }
}
