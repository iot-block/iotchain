package jbok.evm.solidity

import fastparse.ScalaWhitespace._
import fastparse._
import scala.language.postfixOps
import jbok.evm.solidity.Ast.ModifierList.Invocation

import scala.util.Try

/**
  * based on https://github.com/antlr/grammars-v4/blob/master/solidity/Solidity.g4
  */
@SuppressWarnings(Array("org.wartremover.warts.While", "org.wartremover.warts.StringPlusAny"))
object SolidityParser {
  import Ast._

  def parseIdentifier(code: String) = parse(code, identifier(_))

  def parseModifierInvocation(code: String) = parse(code, modifierInvocation(_))

  def parseModifierList(code: String) = parse(code, modifierList(_))

  def parseFunc[T](code: String) = parse(code, functionDefinition(_))

  def parseContract(code: String) = parse(code, contractDefinition(_))

  def parseSource(code: String) = parse(code, sourceUnit(_))

  def parseString(string: String) = parse(string, stringLiteral(_))

  def parseExpr(expr: String, constantMap: Map[String, Int] = Map.empty) =
    parse(expr, constantExpression(constantMap)(_))

  def sourceUnit[_: P] =
    P(
      Start ~ (pragmaDirective.map(_ => None) | importDirective
        .map(_ => None) | contractDefinition.map(Some(_))).rep ~ End)
      .map { contractDefOpts =>
        contractDefOpts
          .foldLeft(List.empty[ContractDef]) {
            case (cds, Some(contractDef)) => cds :+ contractDef
            case (cds, None)              => cds
          }
      }
      .map(Sources.apply)

  def pragmaDirective[_: P] = P("pragma" ~ pragmaName ~ pragmaValue ~ ";")

  def pragmaName[_: P] = P(identifier)

  def pragmaValue[_: P] = P(version | expression)

  def version[_: P] = P(versionConstraint ~ versionConstraint.?)

  def versionOperator[_: P] = P("^" | "~" | ">=" | ">" | "<" | "<=" | "=")

  def versionLiteral[_: P] = P(Basic.Digits ~ "." ~ Basic.Digits ~ "." ~ Basic.Digits)

  def versionConstraint[_: P] = P(versionOperator.? ~ versionLiteral)

  def importDeclaration[_: P] = P(identifier ~ ("as" ~ identifier).?)

  def importDirective[_: P] = P(
    (("import" ~ stringLiteral ~ ("as" ~ identifier).?)
      | ("import" ~ ("*" | identifier) ~ ("as" ~ identifier).? ~ "from" ~ stringLiteral)
      | ("import" ~ "{" ~ importDeclaration ~ ("," ~ importDeclaration).rep ~ "}" ~ "from" ~ stringLiteral)) ~/ ";"
  )

  def contractDefinition[_: P]: P[ContractDef] =
    P(
      ("contract" | "interface" | "library") ~ identifier.! ~ ("is" ~ inheritanceSpecifier ~ ("," ~ inheritanceSpecifier).rep).? ~ "{" ~ contractPart.rep ~ "}"
    ).map {
      case (name, inheritance, parts) =>
        val inhrs = inheritance match {
          case Some((head, xs)) => head :: xs.toList
          case None             => Nil
        }
        ContractDef(name, parts.toList, inhrs)
    }

  def inheritanceSpecifier[_: P]: P[String] =
    P(
      userDefinedTypeName.! ~ ("(" ~ expression ~ ("," ~ expression).rep ~ ")").?
    ).map { case (name, _) => name }

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
      typeName ~ variableModifiers ~ identifier.! ~ ("=" ~ expression.!).? ~ ";"
    ).map {
      case (tnp, vm, name, expression) => StateVariableDef(name, tnp, Some(vm), expression)
    }

  def variableModifiers[_: P]: P[ModifierList] =
    P(
      (ConstantKeyword.map(_ => ModifierList.Constant)
        | PublicKeyword.map(_ => ModifierList.Public)
        | InternalKeyword.map(_ => ModifierList.Internal)
        | PrivateKeyword.map(_ => ModifierList.Private)).rep).map { m =>
      m.foldLeft(ModifierList()) { (l, m) =>
        m match {
          case sm: ModifierList.StateMutability => l.copy(stateMutability = Some(sm))
          case v: ModifierList.Visibility       => l.copy(visibility = Some(v))
          case _: ModifierList.Invocation       => l
        }
      }
    }

  def usingForDeclaration[_: P] =
    P(
      "using" ~ identifier ~ "for" ~ ("*" | typeName) ~ ";"
    ).!.map(OtherDef.apply)

  def structDefinition[_: P] =
    P(
      "struct" ~ identifier.! ~
        "{" ~ (variableDeclaration ~ ";").rep.? ~ "}"
    ).map {
      case (name, a) => StructDefinition(name, a.map(_.toList).getOrElse(Nil))
    }

  def constructorDefinition[_: P] =
    P(
      "constructor" ~ parameterList ~ modifierList ~ block
    ).map { case (params, modifiers) => FunctionDef("constructor", params, modifiers, List.empty) }

  def modifierDefinition[_: P] =
    P(
      "modifier" ~ identifier ~ parameterList.? ~ block
    ).!.map(OtherDef.apply)

  def modifierInvocation[_: P]: P[ModifierList.Invocation] =
    P(
      identifier.! ~ ("(" ~ expressionList.? ~ ")").?
    ).map { case (name, _) => ModifierList.Invocation(name) }

  def functionDefinition[_: P] =
    P(
      "function" ~ identifier.?.! ~ parameterList ~ modifierList ~ returnParameters ~ (";" | block)
    ).map {
      case (name, params, modifiers, returns) => FunctionDef(name, params, modifiers, returns)
    }

  def returnParameters[_: P] = P(
    ("returns" ~ nonEmptyParameterList) | Pass.map(_ => Nil)
  )

  def modifierList[_: P]: P[ModifierList] =
    P(
      (modifierInvocation
        | stateMutability
        | ExternalKeyword.map(_ => ModifierList.External)
        | PublicKeyword.map(_ => ModifierList.Public)
        | InternalKeyword.map(_ => ModifierList.Internal)
        | PrivateKeyword.map(_ => ModifierList.Private)).rep
    ).map { m =>
      m.foldLeft(ModifierList()) { (l, m) =>
        m match {
          case sm: ModifierList.StateMutability => l.copy(stateMutability = Some(sm))
          case v: ModifierList.Visibility       => l.copy(visibility = Some(v))
          case mi: ModifierList.Invocation      => l.copy(modifiers = l.modifiers + mi)
        }
      }
    }

  def eventDefinition[_: P] =
    P(
      "event" ~ identifier ~ eventParameterList ~ AnonymousKeyword.? ~ ";"
    ).!.map(OtherDef.apply)

  def enumValue[_: P] = P(identifier)

  def enumDefinition[_: P] =
    P(
      "enum" ~ identifier.! ~ "{" ~ enumValue.! ~ ("," ~ enumValue.!).rep ~ "}"
    ).map {
      case (id, x, xs) => EnumDefinition(id, (x :: xs.toList).toSet)
    }

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

  def variableDeclaration[_: P]: P[Parameter] =
    P(
      typeName ~ storageLocation.?.! ~ identifier.!.?
    ).map {
      case (tn, _, name) => Parameter(tn, name)
    }

  def typeName[_: P]: P[TypeName] =
    P(
      (elementaryTypeName.map(ElementaryType.apply)
        | mapping
        | functionTypeName
        | userDefinedTypeName) ~ ("[" ~ expression.!.? ~ "]").rep
    ).map {
      case (tne: TypeNameElement, e) => TypeName(tne, e.toList)
    }

  def userDefinedTypeName[_: P]: P[UserDefinedType] =
    P(
      identifier ~ ("." ~ identifier).rep
    ).!.map(UserDefinedType)

  def mapping[_: P]: P[MappingType] =
    P(
      "mapping" ~ "(" ~ elementaryTypeName ~ "=>" ~ typeName ~ ")"
    ).map {
      case (key, m) => MappingType(ElementaryType(key), m)
    }

  def functionTypeName[_: P]: P[FunctionType] =
    P(
      "function" ~ functionTypeParameterList ~
        (InternalKeyword | ExternalKeyword | stateMutability).rep ~
        ("returns" ~ functionTypeParameterList).?
    ).!.map(FunctionType.apply)

  def storageLocation[_: P] = P(
    "memory" | "storage" | "calldata"
  )

  def stateMutability[_: P]: P[ModifierList.StateMutability] = P(
    PureKeyword.map(_ => ModifierList.Pure)
      | ConstantKeyword.map(_ => ModifierList.Constant)
      | ViewKeyword.map(_ => ModifierList.View)
      | PayableKeyword.map(_ => ModifierList.Payable)
  )

  def block[_: P]: P[Unit] =
    P(
      "{" ~ statement.rep ~ "}"
    ).map(_ => ())

  def statement[_: P]: P[_] = P(
    simpleStatement
      | ifStatement
      | whileStatement
      | forStatement
      | inlineAssemblyStatement
      | doWhileStatement
      | continueStatement
      | breakStatement
      | returnStatement
      | throwStatement
      | emitStatement
      | block
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
    ("var" ~ identifierList | "(" ~ variableDeclarationList ~ ")" | variableDeclaration) ~ ("=" ~ expression).? ~ ";"
  )

  def variableDeclarationList[_: P] = P(
    variableDeclaration.? ~ ("," ~ variableDeclaration.?).rep
  )

  def identifierList[_: P] = P(
    "(" ~ (identifier.? ~ ",").rep ~ identifier.? ~ ")"
  )

  def elementaryTypeName[_: P]: P[SolidityType] =
    P(
      int | uint | bytes | ("address" | "bool" | "string" | "var" | "byte").!.map {
        case "address" => AddressType()
        case "bool"    => BoolType()
        case "string"  => StringType()
        case "byte"    => BytesNType(1)
        case "var"     => InvalidSolidityType()
      }
    )

  def int[_: P] =
    P(
      "int104" | "int112" | "int120" | "int128" | "int136" | "int144" | "int152" | "int160" | "int168" | "int176" | "int184" | "int192" | "int200" | "int208" | "int216" | "int224" | "int232" | "int240" | "int248" | "int256" | "int16" | "int24" | "int32" | "int40" | "int48" | "int56" | "int64" | "int72" | "int80" | "int88" | "int96" | "int8" | "int"
    ).!.map(_.split("int")).map {
      case Array(_, bits) => IntType(bits.toInt)
      case Array()        => IntType(256)
    }

  def uint[_: P] =
    P(
      "uint104" | "uint112" | "uint120" | "uint128" | "uint136" | "uint144" | "uint152" | "uint160" | "uint168" | "uint176" | "uint184" | "uint192" | "uint200" | "uint208" | "uint216" | "uint224" | "uint232" | "uint240" | "uint248" | "uint256" | "uint16" | "uint24" | "uint32" | "uint40" | "uint48" | "uint56" | "uint64" | "uint72" | "uint80" | "uint88" | "uint96" | "uint8" | "uint"
    ).!.map(_.split("uint")).map {
      case Array(_, bits) => UIntType(bits.toInt)
      case Array()        => UIntType(256)
    }

  def bytes[_: P] =
    P(
      "bytes10" | "bytes11" | "bytes12" | "bytes13" | "bytes14" | "bytes15" | "bytes16" | "bytes17" | "bytes18" | "bytes19" | "bytes20" | "bytes21" | "bytes22" | "bytes23" | "bytes24" | "bytes25" | "bytes26" | "bytes27" | "bytes28" | "bytes29" | "bytes30" | "bytes31" | "bytes32" | "bytes1" | "bytes2" | "bytes3" | "bytes4" | "bytes5" | "bytes6" | "bytes7" | "bytes8" | "bytes9" | "bytes"
    ).!.map(_.split("bytes")).map {
      case Array(_, bits) => BytesNType(bits.toInt)
      case Array()        => BytesType()
    }

  def expression[_: P]: P[_] = P(
    ("new" ~ typeName | "(" ~ expression ~ ")" | ("++" | "--") ~ expression | ("+" | "-") ~ expression | ("after" | "delete") ~ expression | "!" ~ expression | "~" ~ expression | primaryExpression) ~ exprRest
  )

  def exprRest[_: P]: P[_] = P(
    (("++" | "--")
      | "[" ~ expression ~ "]"
      | "(" ~ functionCallArguments ~ ")"
      | "." ~ identifier
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
      | "?" ~ expression ~ ":" ~ expression
      | ("=" | "|=" | "^=" | "&=" | "<<=" | ">>=" | "+=" | "-=" | "*=" | "/=" | "%=") ~ expression) ~ exprRest
      | Pass
  )

  def constantExpressionEval(tree: (Double, Seq[(String, Double)])) = {
    val (base, ops) = tree
    ops.foldLeft(base) {
      case (left, (op, right)) =>
        op match {
          case "+"  => left + right
          case "-"  => left - right
          case "*"  => left * right
          case "/"  => left / right
          case "%"  => left % right
          case ">>" => left.toInt >> right.toInt
          case "<<" => left.toInt << right.toInt
        }
    }
  }

  def constantExpressionEle[_: P](cm: Map[String, Int]): P[Double] =
    P(
      numberLiteralNoUnit | identifier.!.filter(cm.contains)
    ).map {
      case n: Double => n
      case i: String => cm(i)
    }

  def constantExpressionShift[_: P](cm: Map[String, Int]): P[Double] =
    P(
      constantExpressionAddSub(cm) ~ (("<<" | ">>").! ~/ constantExpressionAddSub(cm)).rep
    ).map(constantExpressionEval)

  def constantExpressionAddSub[_: P](cm: Map[String, Int]): P[Double] =
    P(
      constantExpressionDivMul(cm) ~ (("+" | "-").! ~/ constantExpressionDivMul(cm)).rep
    ).map(constantExpressionEval)

  def constantExpressionDivMul[_: P](cm: Map[String, Int]): P[Double] =
    P(
      constantExpressionExp(cm) ~ (("*" | "/" | "%").! ~/ constantExpressionExp(cm)).rep
    ).map(constantExpressionEval)

  def constantExpressionExp[_: P](cm: Map[String, Int]): P[Double] =
    P(
      constantExpressionUnary(cm) ~ ("**" ~/ constantExpressionUnary(cm)).rep
    ).map {
      case (base, ops) =>
        val all = base :: ops.toList
        all.foldRight(1)((n, p) => scala.math.pow(n, p).toInt)
    }

  def constantExpressionUnary[_: P](cm: Map[String, Int]): P[Double] =
    P(
      ("+" | "-").?.! ~ constantExpressionFactor(cm)
    ).map {
      case ("-", n) => -n
      case (_, n)   => n
    }

  def constantExpressionParens[_: P](cm: Map[String, Int]): P[Double] = P(
    "(" ~/ constantExpressionShift(cm) ~ ")"
  )

  def constantExpressionFactor[_: P](cm: Map[String, Int]): P[Double] = P(
    constantExpressionEle(cm)
      | constantExpressionParens(cm)
  )

  def constantExpression[_: P](cm: Map[String, Int]): P[Double] = P(
    constantExpressionShift(cm)
  )

  def primaryExpression[_: P]: P[Unit] =
    P(
      BooleanLiteral
        | numberLiteral.map(_ => ())
        | HexLiteral
        | stringLiteral
        | identifier
        | tupleExpression.map(_ => ())
        | elementaryTypeNameExpression.map(_ => ())
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
    Identifier ~ "(" ~ functionCallArguments ~ ")"
  )

  def assemblyBlock[_: P]: P[Unit] = P(
    "{" ~ assemblyItem.rep ~ "}"
  )

  def assemblyItem[_: P]: P[Unit] =
    P(
      assemblyBlock
        | assemblyAssignment
        | assemblyExpression
        | assemblyLocalDefinition
        | assemblyStackAssignment
        | labelDefinition
        | assemblySwitch
        | assemblyFunctionDefinition
        | assemblyFor
        | assemblyIf
        | BreakKeyword
        | ContinueKeyword
        | subAssembly
        | numberLiteral.map(_ => ())
        | stringLiteral
        | HexLiteral
        | identifier
    ).map(_ => ())

  def assemblyExpression[_: P]: P[Unit] = P(
    assemblyCall | assemblyLiteral
  )

  def assemblyCall[_: P]: P[Unit] = P(
    (identifier | "return" | "address" | "byte") ~ ("(" ~ (assemblyExpression
      ~ ("," ~ assemblyExpression).rep).? ~ ")").?
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

  def assemblyLiteral[_: P] =
    P(
      stringLiteral | HexNumber.map(_ => ()) | DecimalNumber.map(_ => ()) | HexLiteral
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

  def numberLiteralNoUnit[_: P]: P[Double] = P(
    HexNumber | DecimalNumber
  )

  def identifier[_: P] = P(
    Identifier | "from"
  )

  def VersionLiteral[_: P] = P(
    CharsWhileIn("0-9") ~ "." ~ CharsWhileIn("0-9") ~ "." ~ CharsWhileIn("0-9")
  )

  def BooleanLiteral[_: P] = P(
    "true" | "false"
  )

  def DecimalNumber[_: P]: P[Double] =
    P(
      Basic.Digits | (Basic.Digits ~ "." ~ Basic.Digits) ~ (CharIn("eE") ~ Basic.Digits).?
    ).!.filter(d => Try(d.toDouble).toOption.isDefined).map(d => Try(d.toDouble).toOption.getOrElse(0))

  def HexNumber[_: P]: P[Double] =
    P(
      "0x" ~ HexCharacter.rep
    ).!.map(h => Try(Integer.parseInt(h.substring(2), 16)).toOption.map(_.toDouble).getOrElse(0))

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
        | "typeof"
    )

  def keyword[_: P] = {
    import fastparse.NoWhitespace._

    P(
      (AnonymousKeyword | BreakKeyword | ConstantKeyword | ContinueKeyword | ExternalKeyword | IndexedKeyword | InternalKeyword | PayableKeyword
        | PrivateKeyword | PublicKeyword | PureKeyword | ViewKeyword | ReturnsKeyword | reservedKeyword | elementaryTypeName) ~
        !CharPred(c => Character.isLetter(c) | Character.isDigit(c) | c == '$' | c == '_'))
  }

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
  def ReturnsKeyword[_: P]   = P("returns")
  def ByteKeyword[_: P]      = P("byte")
  def AddressKeyword[_: P]   = P("address")
  def FromKeyword[_: P]      = P("from")

  def Identifier[_: P] =
    P(!keyword ~ identifierStart ~ CharsWhileIn("a-zA-Z0-9$_", 0))

  def identifierStart[_: P] = P(CharIn("a-zA-Z$_"))

  def identifierPart[_: P] = P(CharIn("a-zA-Z0-9$_"))

  def stringLiteral[_: P] = {
    import fastparse.NoWhitespace._

    P("\"" ~ doubleQuotedStringCharacter.rep ~ "\"" | "'" ~ singleQuotedStringCharacter.rep ~ "'")
  }

  def doubleQuotedStringCharacter[_: P] = P(CharPred(c => c != '"' && c != '\r' && c != '\n') | ("\\" ~ AnyChar))

  def singleQuotedStringCharacter[_: P] = P(CharPred(c => c != '\'' && c != '\r' && c != '\n') | ("\\" ~ AnyChar))

  object Basic {
    def UnicodeEscape[_: P] = P("u" ~ HexDigit ~ HexDigit ~ HexDigit ~ HexDigit)

    //Numbers and digits
    def Digit[_: P]       = P(CharIn("0-9"))
    def Digits[_: P]      = Digit.rep(1)
    def DigitNoZero[_: P] = P(CharIn("1-9"))
    def Number[_: P]      = P(DigitNoZero ~ Digit.rep)

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
