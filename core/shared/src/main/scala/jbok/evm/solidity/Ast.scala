package jbok.evm.solidity

import io.circe.generic.JsonCodec

object Ast {
  import ABIDescription._
  import ModifierList._
  final case class ContractDef(name: String, parts: List[ContractPart]) {
    lazy val functionsABI = {
      parts.collect {
        case FunctionDef(name, inputs, m, outputs) if m.visibility.isEmpty || m.visibility.contains(Public) =>
          FunctionDescription(
            name,
            inputs.map(p => ParameterDescription(p.name, p.typeName.parameterType(constantNumericMap))),
            outputs.map(p => ParameterDescription(p.name, p.typeName.parameterType(constantNumericMap))),
            if (m.stateMutability.contains(View) || m.stateMutability.contains(Pure)) "view"
            else if (m.stateMutability.contains(Payable)) "payable"
            else "nonpayable"
          )
      }
    }

    lazy val constantNumericMap = {
      val cns = parts.collect {
        case StateVariableDef(name, typeName, Some(modifiers), Some(expression))
            if modifiers.stateMutability.contains(Constant) =>
          typeName.typeNameElement match {
            case et: ElementaryType if expression.nonEmpty && (et.solidityType match {
                  case IntType(_) | UIntType(_) => true
                  case _                        => false
                }) =>
              Some(name -> expression)
            case _ => None
          }
      }

      cns.foldLeft(Map.empty[String, Int]) {
        case (m, Some((name, expr))) =>
          val exprResult = SolidityParser.parseExpr(expr, m)
          if (exprResult.isSuccess) m + (name -> SolidityParser.parseExpr(expr, m).get.value.toInt)
          else m
        case (m, _) => m
      }
    }

    lazy val variablesABI = {
      parts.collect {
        case StateVariableDef(name, typeName, Some(modifiers), expression)
            if (modifiers.visibility.isEmpty || modifiers.visibility.contains(Public)) && !modifiers.stateMutability
              .contains(Constant) && !typeName.typeNameElement.isInstanceOf[FunctionType] && !typeName.typeNameElement
              .isInstanceOf[UserDefinedType] =>
          def getParameterList(typeName: TypeName): (List[ParameterDescription], List[ParameterDescription]) = {
            val sizeArrayList = typeName.arrayExpr.map { exprOpt =>
              val sizeOpt = exprOpt.map(SolidityParser.parseExpr(_, constantNumericMap).get.value.toInt)
              sizeOpt match {
                case None    => 0
                case Some(s) => s
              }
            }

            typeName.typeNameElement match {
              case et: ElementaryType =>
                (Nil, List(ParameterDescription(None, typeName.parameterType(constantNumericMap))))
              case MappingType(et, tn) =>
                val (inputs, outputs) = getParameterList(tn)
                val newInputs = sizeArrayList.map(_ => ParameterDescription(None, ParameterType(UIntType(256), Nil))) :+
                  ParameterDescription(None, ParameterType(et.solidityType, Nil))
                (newInputs ++ inputs, outputs)
              case _ => (Nil, Nil)
            }
          }

          val (inputs, outputs) = getParameterList(typeName)
          FunctionDescription(name, inputs, outputs, "view")
      }
    }

    def toABI: ContractDescription = {
      val all = functionsABI ++ variablesABI

      ContractDescription(name, all)
    }
  }

  sealed trait ContractPart
  final case class FunctionDef(name: String,
                               paramList: List[Parameter],
                               modifiers: ModifierList,
                               returnList: List[Parameter])
      extends ContractPart {
    override def toString: String = s"$modifiers $name: (${paramList.mkString(", ")}) => (${returnList.mkString(", ")})"
  }
  final case class StructDefinition(id: String, params: List[Parameter]) extends ContractPart
  final case class StateVariableDef(id: String,
                                    typeName: TypeName,
                                    modifiers: Option[ModifierList],
                                    expression: Option[String])
      extends ContractPart
  final case class OtherDef(name: String) extends ContractPart

  final case class Parameter(typeName: TypeName, name: Option[String])

  final case class TypeName(typeNameElement: TypeNameElement, arrayExpr: List[Option[String]]) {
    def parameterType(cm: Map[String, Int]): ParameterType = {
      val sizeArrayList = arrayExpr.map { exprOpt =>
        val sizeOpt = exprOpt.map(SolidityParser.parseExpr(_, cm).get.value.toInt)
        sizeOpt match {
          case None    => 0
          case Some(s) => s
        }
      }

      typeNameElement match {
        case t: ElementaryType => ParameterType(t.solidityType, sizeArrayList)
        case _                 => ParameterType(InvalidSolidityType(), Nil)
      }
    }
  }

  sealed trait TypeNameElement
  final case class ElementaryType(solidityType: SolidityType)        extends TypeNameElement
  final case class FunctionType(functionType: String)                extends TypeNameElement
  final case class UserDefinedType(userDefinedType: String)          extends TypeNameElement
  final case class MappingType(key: ElementaryType, value: TypeName) extends TypeNameElement

  import ModifierList._
  final case class ModifierList(modifiers: Set[Invocation] = Set.empty,
                                visibility: Option[Visibility] = None,
                                stateMutability: Option[StateMutability] = None)

  object ModifierList {
    sealed trait Modifier

    final case class Invocation(s: String) extends Modifier

    sealed trait Visibility extends Modifier
    case object Public      extends Visibility
    case object Private     extends Visibility
    case object Internal    extends Visibility
    case object External    extends Visibility

    sealed trait StateMutability extends Modifier
    case object Pure             extends StateMutability
    case object Constant         extends StateMutability
    case object Payable          extends StateMutability
    case object View             extends StateMutability
  }
}
