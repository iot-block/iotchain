package jbok.evm.solidity

import fastparse.ScalaWhitespace._
import fastparse._

/**
  * based on https://github.com/antlr/grammars-v4/blob/master/solidity/Solidity.g4
  */
@SuppressWarnings(Array("org.wartremover.warts.While", "org.wartremover.warts.StringPlusAny"))
object SolidityParser {
  import Ast._

  def parseFunc[T](code: String) = parse(code, functionDefinition(_))

  def parseContract(code: String) = parse(code, contractDefinition(_))

  def sourceUnit[_: P] = P(pragmaDirective | importDirective | contractDefinition)

  def pragmaDirective[_: P] = P("pragma" ~ pragmaName ~ pragmaValue)

  def pragmaName[_: P] = P(identifier)

  def pragmaValue[_: P] = P(version | expression)

  def version[_: P] = P(versionConstraint ~ versionConstraint.?)

  def versionOperator[_: P] = P("^" | "~" | ">=" | ">" | "<" | "<=" | "=")

  def versionLiteral[_: P] = P("""[0-9]+ "." [0-9]+ "." [0-9]+""")

  def versionConstraint[_: P] = P(versionOperator.? ~ versionLiteral)

  def importDeclaration[_: P] = P(identifier ~ ("as" ~ identifier).?)

  def importDirective[_: P] = P(
    ("import" ~ stringLiteral ~ ("as" ~ identifier).?)
      | ("import" ~ ("*" | identifier) ~ ("as" ~ identifier).? ~ "from" ~ stringLiteral)
      | ("import" ~ "{" ~ importDeclaration ~ ("," ~ importDeclaration).rep ~ "}" ~ "from" ~ stringLiteral)
  )

  def contractDefinition[_: P]: P[ContractDef] =
    P(
      ("contract" | "interface" | "library") ~ identifier.! ~ ("is" ~ inheritanceSpecifier ~ ("," ~ inheritanceSpecifier).rep).? ~ "{" ~ contractPart.rep / "}"
    ).map { case (name, parts) => ContractDef(name, parts.toList) }

  def inheritanceSpecifier[_: P]: P[Unit] =
    P(
      userDefinedTypeName ~ ("(" ~ expression ~ ("," ~ expression).rep ~ ")").?
    ).map(_ => ())

  def contractPart[_: P]: P[ContractPart] = P(
    stateVariableDeclaration
      | usingForDeclaration
      | structDefinition
      | constructorDefinition
      | modifierDefinition
      | functionDefinition
      | eventDefinition
      | enumDefinition
  )

  def stateVariableDeclaration[_: P] =
    P(
      typeName ~ (PublicKeyword | InternalKeyword | PrivateKeyword | ConstantKeyword).rep
        ~ identifier ~ ("=" ~ expression).? ~ ";"
    ).!.map(OtherDef.apply)

  def usingForDeclaration[_: P] =
    P(
      "using" ~ identifier ~ "for" ~ ("*" | typeName)
    ).!.map(OtherDef.apply)

  def structDefinition[_: P] =
    P(
      "struct" ~ identifier ~
        "{" ~ (variableDeclaration ~ ";" ~ (variableDeclaration ~ ";").rep).? ~ "}"
    ).!.map(OtherDef.apply)

  def constructorDefinition[_: P] =
    P(
      "constructor" ~ parameterList ~ modifierList ~ block
    ).!.map(OtherDef.apply)

  def modifierDefinition[_: P] =
    P(
      "modifier" ~ identifier ~ parameterList.? ~ block
    ).!.map(OtherDef.apply)

  def modifierInvocation[_: P]: P[Unit] =
    P(
      identifier ~ ("(" ~ expressionList.? ~ ")").?
    ).map(_ => ())

  def functionDefinition[_: P] =
    P(
      "function" ~ identifier.?.! ~ parameterList ~ modifierList.! ~ returnParameters ~ (";" | block)
    ).map { case (name, params, modifiers, returns) => FunctionDef(name, params, modifiers, returns) }

  def returnParameters[_: P] = P(
    ("returns" ~ nonEmptyParameterList) | Pass.map(_ => Nil)
  )

  def modifierList[_: P]: P[Unit] = P(
    (//      modifierInvocation |
    stateMutability | ExternalKeyword
      | PublicKeyword | InternalKeyword | PrivateKeyword).rep
  )

  def eventDefinition[_: P] =
    P(
      "event" ~ identifier ~ eventParameterList ~ AnonymousKeyword.? ~ ";"
    ).!.map(OtherDef.apply)

  def enumValue[_: P] = P(identifier)

  def enumDefinition[_: P] =
    P(
      "enum" ~ identifier ~ "{" ~ enumValue.? ~ ("," ~ enumValue).rep ~ "}"
    ).!.map(OtherDef.apply)

  def parameterList[_: P]: P[List[Parameter]] =
    P(
      "(" ~ (parameter ~ ("," ~ parameter).rep).? ~ ")"
    ).map {
      case Some((x, xs)) => x :: xs.toList
      case None          => Nil
    }

  def nonEmptyParameterList[_: P]: P[List[Parameter]] =
    P(
      "(" ~ parameter ~ ("," ~ parameter).rep ~ ")"
    ).map { case (x, xs) => x :: xs.toList }

  def parameter[_: P]: P[Parameter] =
    P(
      typeName ~ storageLocation.? ~ identifier.!.?
    ).map { case (typeName, id) => Parameter(typeName, id) }

  def eventParameterList[_: P] = P(
    "(" ~ (eventParameter ~ ("," ~ eventParameter).rep).? ~ ")"
  )

  def eventParameter[_: P] = P(
    typeName ~ IndexedKeyword.? ~ identifier.?
  )

  def functionTypeParameterList[_: P] = P(
    "(" ~ (functionTypeParameter ~ ("," ~ functionTypeParameter).rep).? ~ ")"
  )

  def functionTypeParameter[_: P] = P(
    typeName ~ storageLocation.?
  )

  def variableDeclaration[_: P] = P(
    typeName ~ storageLocation.? ~ identifier
  )

  def typeName[_: P]: P[TypeName] = P(
    elementaryTypeName
      | mapping
      | functionTypeName
      | (userDefinedTypeName ~ "[" ~ expression.? ~ "]").!.map(OtherType.apply)
      | userDefinedTypeName
  )

  def userDefinedTypeName[_: P] =
    P(
      identifier ~ ("." ~ identifier).rep
    ).!.map(OtherType.apply)

  def mapping[_: P]: P[MappingType] =
    P(
      "mapping" ~ "(" ~ elementaryTypeName.! ~ "=>" ~ typeName.! ~ ")"
    ).map { case (from, to) => MappingType(from, to) }

  def functionTypeName[_: P]: P[OtherType] =
    P(
      "function" ~ functionTypeParameterList ~
        (InternalKeyword | ExternalKeyword | stateMutability).rep ~
        ("returns" ~ functionTypeParameterList).?
    ).!.map(OtherType.apply)

  def storageLocation[_: P] = P(
    "memory" | "storage" | "calldata"
  )

  def stateMutability[_: P] = P(
    PureKeyword | ConstantKeyword | ViewKeyword | PayableKeyword
  )

  def block[_: P]: P[Unit] =
    P(
      "{" ~ statement.rep ~ "}"
    ).map(_ => ())

  def statement[_: P]: P[_] = P(
    ifStatement
      | whileStatement
      | forStatement
      | block
      | inlineAssemblyStatement
      | doWhileStatement
      | continueStatement
      | breakStatement
      | returnStatement
      | throwStatement
      | emitStatement
      | simpleStatement
  )

  def expressionStatement[_: P] = P(
    expression ~ ";"
  )

  def ifStatement[_: P] = P(
    "if" ~ "(" ~ expression ~ ")" ~ statement ~ ("else" ~ statement).?
  )

  def whileStatement[_: P] = P(
    "while" ~ "(" ~ expression ~ ")" ~ statement
  )

  def simpleStatement[_: P] = P(
    variableDeclarationStatement | expressionStatement
  )

  def forStatement[_: P] = P(
    "for" ~ "(" ~ (simpleStatement | ";") ~ expression.? ~ ";" ~ expression.? ~ ")" ~ statement
  )

  def inlineAssemblyStatement[_: P] = P(
    "assembly" ~ stringLiteral.? ~ assemblyBlock
  )

  def doWhileStatement[_: P] = P(
    "do" ~ statement ~ "while" ~ "(" ~ expression ~ ")" ~ ";"
  )

  def continueStatement[_: P] = P(
    "continue" ~ ";"
  )

  def breakStatement[_: P] = P(
    "break" ~ ";"
  )

  def returnStatement[_: P] = P(
    "return" ~ expression.? ~ ";"
  )

  def throwStatement[_: P] = P(
    "throw" ~ ";"
  )

  def emitStatement[_: P] = P(
    "emit" ~ functionCall ~ ";"
  )

  def variableDeclarationStatement[_: P] = P(
    ("var" ~ (identifierList | variableDeclaration | "(" ~ variableDeclarationList ~ ")")) ~ ("=" ~ expression).? ~ ";"
  )

  def variableDeclarationList[_: P] = P(
    variableDeclaration.? ~ ("," ~ variableDeclaration ?).rep
  )

  def identifierList[_: P] = P(
    "(" ~ (identifier.? ~ ",").rep ~ identifier.? ~ ")"
  )

  def elementaryTypeName[_: P]: P[ElementaryType] =
    P(
      int | uint | bytes | fixed | ufixed | ("address" | "bool" | "string" | "var" | "byte").!.map(
        FixedElementType.apply)
    )

  def int[_: P] =
    P(
      "int8" | "int16" | "int24" | "int32" | "int40" | "int48" | "int56" | "int64" | "int72" | "int80" | "int88" | "int96" | "int104" | "int112" | "int120" | "int128" | "int136" | "int144" | "int152" | "int160" | "int168" | "int176" | "int184" | "int192" | "int200" | "int208" | "int216" | "int224" | "int232" | "int240" | "int248" | "int256" | "int"
    ).!.map(_.split("int")).map {
      case Array(_, bits) => VarValue("int", bits.toInt)
      case Array(_)       => VarValue("int", 256)
    }

  def uint[_: P] =
    P(
      "uint8" | "uint16" | "uint24" | "uint32" | "uint40" | "uint48" | "uint56" | "uint64" | "uint72" | "uint80" | "uint88" | "uint96" | "uint104" | "uint112" | "uint120" | "uint128" | "uint136" | "uint144" | "uint152" | "uint160" | "uint168" | "uint176" | "uint184" | "uint192" | "uint200" | "uint208" | "uint216" | "uint224" | "uint232" | "uint240" | "uint248" | "uint256" | "uint"
    ).!.map(_.split("uint")).map {
      case Array(_, bits) => VarValue("uint", bits.toInt)
      case Array()        => VarValue("uint", 256)
    }

  def bytes[_: P] =
    P(
      "bytes" | "bytes1" | "bytes2" | "bytes3" | "bytes4" | "bytes5" | "bytes6" | "bytes7" | "bytes8" | "bytes9" | "bytes10" | "bytes11" | "bytes12" | "bytes13" | "bytes14" | "bytes15" | "bytes16" | "bytes17" | "bytes18" | "bytes19" | "bytes20" | "bytes21" | "bytes22" | "bytes23" | "bytes24" | "bytes25" | "bytes26" | "bytes27" | "bytes28" | "bytes29" | "bytes30" | "bytes31" | "bytes32"
    ).!.map(_.split("bytes")).map {
      case Array(_, bits) => VarValue("bytes", bits.toInt)
      case Array()        => VarValue("bytes", 256)
    }

  def fixed[_: P] =
    P(
      "fixed" | ("fixed" ~ Basic.Digits ~ "x" ~ Basic.Digits)
    ).!.map(_.split("fixed").lift(1).map(_.split("x"))).map {
      case Some(Array(m, n)) => FixPointNumber(true, m.toInt, n.toInt)
      case None              => FixPointNumber(true, 128, 18)
    }

  def ufixed[_: P] =
    P(
      "ufixed" | ("ufixed" ~ Basic.Digits ~ "x" ~ Basic.Digits)
    ).!.map(_.split("ufixed").lift(1).map(_.split("x"))).map {
      case Some(Array(m, n)) => FixPointNumber(false, m.toInt, n.toInt)
      case None              => FixPointNumber(false, 128, 18)
    }

  def expression[_: P]: P[_] = P(
    //    calcExpression | primaryExpression.log("primary")
    primaryExpression ~ exprRest
  )

  def exprRest[_: P]: P[_] = P(
    ("++" | "--")
      | "new" ~ typeName
      | "[" ~ expression ~ "]" ~ exprRest
      | "(" ~ functionCallArguments ~ ")" ~ exprRest
      | "." ~ identifier ~ exprRest
      | "(" ~ expression ~ ")"
      | ("++" | "--") ~ expression
      | ("+" | "-") ~ expression
      | ("after" | "delete") ~ expression
      | "!" ~ expression
      | "~" ~ expression
      | "**" ~ expression
      | ("*" | "/" | "%") ~ expression
      | ("+" | "-") ~ expression
      | ("<<" | ">>") ~ expression
      | "&" ~ expression
      | "^" ~ expression
      | "|" ~ expression
      | ("<=" | ">=" | "<" | ">") ~ expression
      | ("==" | "!=") ~ expression
      | "&&" ~ expression
      | "||" ~ expression
      | "?" ~ expression ~ exprRest ~ ":" ~ expression ~ exprRest
      | ("=" | "|=" | "^=" | "&=" | "<<=" | ">>=" | "+=" | "-=" | "*=" | "/=" | "%=") ~ expression
      | Pass
  )

  def primaryExpression[_: P] = P(
    BooleanLiteral | numberLiteral | HexLiteral | stringLiteral | identifier | tupleExpression | elementaryTypeNameExpression
  )

  def expressionList[_: P] = P(
    expression ~ ("," ~ expression).rep
  )

  def nameValueList[_: P] = P(
    nameValue ~ ("," ~ nameValue).rep ~ ",".?
  )

  def nameValue[_: P] = P(
    identifier ~ ":" ~ expression
  )

  def functionCallArguments[_: P] = P(
    "{" ~ nameValueList.? ~ "}" | expressionList.?
  )

  def functionCall[_: P] = P(
    expression ~ "(" ~ functionCallArguments ~ ")"
  )

  def assemblyBlock[_: P]: P[Unit] = P(
    "{" ~ assemblyItem.rep ~ "}"
  )

  def assemblyItem[_: P]: P[Unit] = P(
    identifier
      | assemblyBlock
      | assemblyExpression
      | assemblyLocalDefinition
      | assemblyAssignment
      | assemblyStackAssignment
      | labelDefinition
      | assemblySwitch
      | assemblyFunctionDefinition
      | assemblyFor
      | assemblyIf
      | BreakKeyword
      | ContinueKeyword
      | subAssembly
      | numberLiteral
      | stringLiteral
      | HexLiteral
  )

  def assemblyExpression[_: P]: P[Unit] = P(
    assemblyCall | assemblyLiteral
  )

  def assemblyCall[_: P]: P[Unit] = P(
    ("return" | "address" | "byte" | identifier) ~ ("(" ~ assemblyExpression.? ~ ("," ~ assemblyExpression).rep ~ ")").?
  )

  def assemblyLocalDefinition[_: P] = P(
    "let" ~ assemblyIdentifierOrList ~ (":=" ~ assemblyExpression).?
  )

  def assemblyAssignment[_: P] = P(
    assemblyIdentifierOrList ~ ":=" ~ assemblyExpression
  )

  def assemblyIdentifierOrList[_: P] = P(
    identifier | "(" ~ assemblyIdentifierList ~ ")"
  )

  def assemblyIdentifierList[_: P] = P(
    identifier ~ ("," ~ identifier).rep
  )

  def assemblyStackAssignment[_: P] = P(
    "=:" ~ identifier
  )

  def labelDefinition[_: P] = P(
    identifier ~ ":"
  )

  def assemblySwitch[_: P] = P(
    "switch" ~ assemblyExpression ~ assemblyCase.rep
  )

  def assemblyCase[_: P] = P(
    "case" ~ assemblyLiteral ~ assemblyBlock
      | "default" ~ assemblyBlock
  )

  def assemblyFunctionDefinition[_: P] = P(
    "function" ~ identifier ~ "(" ~ assemblyIdentifierList.? ~ ")"
      ~ assemblyFunctionReturns.? ~ assemblyBlock
  )

  def assemblyFunctionReturns[_: P] = P(
    "->" ~ assemblyIdentifierList
  )

  def assemblyFor[_: P] = P(
    "for" ~ (assemblyBlock | assemblyExpression) ~
      assemblyExpression ~ (assemblyBlock | assemblyExpression) ~ assemblyBlock
  )

  def assemblyIf[_: P] = P(
    "if" ~ assemblyExpression ~ assemblyBlock
  )

  def assemblyLiteral[_: P] = P(
    stringLiteral ~ DecimalNumber ~ HexNumber ~ HexLiteral
  )

  def subAssembly[_: P] = P(
    "assembly" ~ identifier ~ assemblyBlock
  )

  def tupleExpression[_: P] = P(
    "(" ~ (expression.? ~ ("," ~ expression ?).rep) ~ ")"
      | "[" ~ (expression ~ ("," ~ expression).rep).? ~ "]"
  )

  def elementaryTypeNameExpression[_: P] = P(
    elementaryTypeName
  )

  def numberLiteral[_: P] = P(
    (HexNumber | DecimalNumber) ~ NumberUnit.?
  )

  def identifier[_: P] = P(
    "from" | Identifier
  )

  def VersionLiteral[_: P] = P(
    CharsWhileIn("0-9") ~ "." ~ CharsWhileIn("0-9") ~ "." ~ CharsWhileIn("0-9")
  )

  def BooleanLiteral[_: P] = P(
    "true" | "false"
  )

  def DecimalNumber[_: P] = P(
    Basic.Digits | (Basic.Digits ~ "." ~ Basic.Digits) ~ (CharIn("eE") ~ Basic.Digits).?
  )

  def HexNumber[_: P] = P(
    "0x" ~ HexCharacter.rep
  )

  def NumberUnit[_: P] = P(
    "wei" | "szabo" | "finney" | "ether" | "seconds" | "minutes" | "hours" | "days" | "weeks" | "years"
  )

  def HexLiteral[_: P] = P(
    "hex" ~ ("\"" ~ HexPair.rep ~ "\"" | "\'" ~ HexPair.rep ~ "\'")
  )

  def HexPair[_: P] = P(
    HexCharacter ~ HexCharacter
  )

  def HexCharacter[_: P] = P(
    CharIn("0-9A-Fa-f")
  )

  def reservedKeyword[_: P] =
    P(
      "abstract"
        | "after"
        | "case"
        | "catch"
        | "default"
        | "final"
        | "in"
        | "inline"
        | "let"
        | "match"
        | "null"
        | "of"
        | "relocatable"
        | "static"
        | "switch"
        | "try"
        | "type"
        | "typeof")

  def AnonymousKeyword[_: P] = P("anonymous")
  def BreakKeyword[_: P]     = P("break")
  def ConstantKeyword[_: P]  = P("constant")
  def ContinueKeyword[_: P]  = P("continue")
  def ExternalKeyword[_: P]  = P("external")
  def IndexedKeyword[_: P]   = P("indexed")
  def InternalKeyword[_: P]  = P("internal")
  def PayableKeyword[_: P]   = P("payable")
  def PrivateKeyword[_: P]   = P("private")
  def PublicKeyword[_: P]    = P("public")
  def PureKeyword[_: P]      = P("pure")
  def ViewKeyword[_: P]      = P("view")

  def Identifier[_: P] = P(identifierStart ~ identifierPart.rep)

  def identifierStart[_: P] = P(CharIn("a-zA-Z$_"))

  def identifierPart[_: P] = P(CharIn("a-zA-Z0-9$_"))

  def stringLiteral[_: P] =
    P("\"" ~ doubleQuotedStringCharacter.rep ~ "\"" | "'" ~ singleQuotedStringCharacter.rep ~ "'")

  def doubleQuotedStringCharacter[_: P] = P(CharPred(c => c != '"' && c != '\r' && c != '\n') | ("\\" ~ AnyChar))

  def singleQuotedStringCharacter[_: P] = P(CharPred(c => c != '\'' && c != '\r' && c != '\n') | ("\\" ~ AnyChar))

  object Basic {
    def UnicodeEscape[_: P] = P("u" ~ HexDigit ~ HexDigit ~ HexDigit ~ HexDigit)

    //Numbers and digits
    def Digit[_: P]  = P(CharIn("0-9"))
    def Digits[_: P] = Digit.rep(1)

    def HexDigit[_: P]  = P(CharIn("0-9a-fA-F"))
    def HexNum[_: P]    = P("0x" ~ CharsWhileIn("0-9a-fA-F"))
    def DecNum[_: P]    = P(CharsWhileIn("0-9"))
    def Exp[_: P]       = P(CharIn("Ee") ~ CharIn("+\\-").? ~ DecNum)
    def FloatType[_: P] = P(CharIn("fFdD"))

    def WSChars[_: P] = P(NoTrace(CharsWhileIn("\u0020\u0009")))
    def Newline[_: P] = P(NoTrace(StringIn("\r\n", "\n")))
    def Semi[_: P]    = P(";" | Newline.rep(1))
  }

  object Literals {
    def CommentChunk[_: P]              = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)
    def MultilineComment[_: P]: P[Unit] = P("/*" ~ CommentChunk.rep ~ "*/")
    def SameLineCharChunks[_: P]        = P(CharsWhile(c => c != '\n' && c != '\r') | !Basic.Newline ~ AnyChar)
    def LineComment[_: P]               = P("//" ~ SameLineCharChunks.rep ~ &(Basic.Newline | End))
    def Comment[_: P]                   = P(MultilineComment | LineComment)
  }
}
