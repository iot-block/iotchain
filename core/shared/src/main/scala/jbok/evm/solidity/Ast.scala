package jbok.evm.solidity

object Ast {
  final case class ContractDef(name: String, parts: List[ContractPart])

  sealed trait ContractPart
  final case class FunctionDef(name: String, paramList: List[Parameter], modifiers: String, returnList: List[Parameter]) extends ContractPart {
    override def toString: String = s"$modifiers $name: (${paramList.mkString(", ")}) => (${returnList.mkString(", ")})"
  }
  final case class OtherDef(name: String) extends ContractPart

  final case class Parameter(typeName: TypeName, name: Option[String]) {
    override def toString: String = s"${name.getOrElse("")}: $typeName"
  }

  sealed trait TypeName
  sealed trait ElementaryType extends TypeName
  final case class FixedElementType(name: String) extends ElementaryType {
    override def toString: String = name
  }
  final case class VarValue(base: String, bits: Int) extends ElementaryType {
    override def toString: String = s"$base@$bits"
  }
  final case class FixPointNumber(signed: Boolean, m: Int, n: Int) extends ElementaryType {
    override def toString: String = if (signed) s"fixed${m}x${n}" else s"ufixed${m}x${n}"
  }

  final case class MappingType(from: String, to: String) extends TypeName {
    override def toString: String = s"mapping($from => $to)"
  }
  final case class OtherType(name: String) extends TypeName
}

