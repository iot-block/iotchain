package jbok.evm.solidity

object Ast {
  import ABIDescription._
  import ModifierList._
  final case class Sources(contractDefs: List[ContractDef]) {
    def ABI: List[ContractDescription] =
      contractDefs.foldLeft(List.empty[ContractDescription]) {
        case (cs, contractDef) =>
          cs :+ contractDef.toABI(cs)
      }
  }

  final case class ContractDef(name: String, parts: List[ContractPart], inheritances: List[String]) {
    def functionsABI(contractDescriptions: List[ContractDescription] = List.empty) =
      parts.collect {
        case FunctionDef(name, inputs, m, outputs)
            if !Set("constructor", this.name)
              .contains(name) && (m.visibility.isEmpty || m.visibility.contains(Public)) =>
          FunctionDescription(
            name,
            inputs.map(p =>
              ParameterDescription(p.name, p.typeName.parameterType(contractDescriptions, constantNumericMap))),
            outputs.map(p =>
              ParameterDescription(p.name, p.typeName.parameterType(contractDescriptions, constantNumericMap))),
            if (m.stateMutability.contains(View) || m.stateMutability.contains(Pure) || m.stateMutability.contains(
                  Constant)) "view"
            else if (m.stateMutability.contains(Payable)) "payable"
            else "nonpayable"
          )
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

    def enumSizeToUint(size: Int) = (1 to 16).toList.find(i => BigInt(2).pow(i * 8) > size).getOrElse(16).toString

    lazy val enumDefinitionMap = {
      val eds = parts.collect {
        case EnumDefinition(id, values) =>
          id -> s"uint${enumSizeToUint(values.size)}"
      }

      eds.toMap
    }

    def variablesABI(contractDescriptions: List[ContractDescription] = List.empty) =
      parts.collect {
        case StateVariableDef(name, typeName, Some(modifiers), _)
            if modifiers.visibility.contains(Public) && !modifiers.stateMutability
              .contains(Constant) && !typeName.typeNameElement.isInstanceOf[FunctionType] && (!typeName.typeNameElement
              .isInstanceOf[UserDefinedType] || (typeName.typeNameElement
              .isInstanceOf[UserDefinedType] && contractDescriptions
              .map(_.name)
              .toSet
              .contains(typeName.typeNameElement
                .asInstanceOf[UserDefinedType]
                .userDefinedType))) =>
          def getParameterList(typeName: TypeName): (List[ParameterDescription], List[ParameterDescription]) = {
            val sizeArrayList = typeName.arrayExpr.map { exprOpt =>
              val sizeOpt = exprOpt.map(SolidityParser.parseExpr(_, constantNumericMap).get.value.toInt)
              sizeOpt match {
                case None    => 0
                case Some(s) => s
              }
            }

            typeName.typeNameElement match {
              case _: ElementaryType =>
                (Nil,
                 List(ParameterDescription(None, typeName.parameterType(contractDescriptions, constantNumericMap))))
              case MappingType(et, tn) =>
                val (inputs, outputs) = getParameterList(tn)
                val newInputs = sizeArrayList.map(_ => ParameterDescription(None, ParameterType(UIntType(256), Nil))) :+
                  ParameterDescription(None, ParameterType(et.solidityType, Nil))
                (newInputs ++ inputs, outputs)
              case udt: UserDefinedType if contractDescriptions.map(_.name).toSet.contains(udt.userDefinedType) =>
                (Nil,
                 List(ParameterDescription(None, typeName.parameterType(contractDescriptions, constantNumericMap))))
              case _ => (Nil, Nil)
            }
          }

          val (inputs, outputs) = getParameterList(typeName)
          FunctionDescription(name, inputs, outputs, "view")
      }

    def toABI(contractDescriptions: List[ContractDescription] = List.empty): ContractDescription = {
      val thisFunctionDescriptions = functionsABI(contractDescriptions) ++ variablesABI(contractDescriptions)
      val cdMap                    = contractDescriptions.map(cd => cd.name -> cd).toMap
      val all = (inheritances.map(cdMap(_).methods).foldLeft(Map.empty[String, FunctionDescription]) {
        case (methods, fds) => methods ++ fds.map(fd => fd.name -> fd)
      }
        ++ thisFunctionDescriptions.map(fd => fd.name -> fd)).values.toList

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
  final case class EnumDefinition(id: String, values: Set[String]) extends ContractPart
  final case class OtherDef(name: String)                          extends ContractPart

  final case class Parameter(typeName: TypeName, name: Option[String])

  final case class TypeName(typeNameElement: TypeNameElement, arrayExpr: List[Option[String]]) {
    def parameterType(contractDescriptions: List[ContractDescription], cm: Map[String, Int]): ParameterType = {
      val sizeArrayList = arrayExpr.map { exprOpt =>
        val sizeOpt = exprOpt.map(SolidityParser.parseExpr(_, cm).get.value.toInt)
        sizeOpt match {
          case None    => 0
          case Some(s) => s
        }
      }

      typeNameElement match {
        case et: ElementaryType => ParameterType(et.solidityType, sizeArrayList)
        case udt: UserDefinedType if contractDescriptions.map(_.name).toSet.contains(udt.userDefinedType) =>
          ParameterType(AddressType(), Nil)
        case _ => ParameterType(InvalidSolidityType(), Nil)
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
