package jbok.network.rpc.internal

import cats.effect.Sync
import jbok.network.rpc.{PathName, RpcService}

import scala.reflect.macros.blackbox

class Translator[C <: blackbox.Context](val c: C) {
  import c.universe._
  import jbok.network.rpc.RpcValidator._

  val rootPkg         = q"jbok.network.rpc"
  val rootInternalPkg = q"jbok.network.rpc.internal"

  def abort(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

  private def validateMethod(expectedReturnType: Type, symbol: MethodSymbol, methodType: Type): Either[String, (MethodSymbol, Type)] =
    for {
      _ <- methodType match {
        case _: MethodType | _: NullaryMethodType => Valid
        case _: PolyType                          => Invalid(s"method ${symbol.name} has type parameters")
        case _                                    => Invalid(s"method ${symbol.name} has unsupported type")
      }
      methodResult = methodType.finalResultType.typeConstructor
      returnResult = expectedReturnType.finalResultType.typeConstructor
      _ <- validate(methodResult <:< returnResult, s"method ${symbol.name} has invalid return type, required: $methodResult <: $returnResult")
    } yield (symbol, methodType)

  private def eitherSeq[A, B](list: List[Either[A, B]]): Either[List[A], List[B]] =
    list.partition(_.isLeft) match {
      case (Nil, rights) => Right(rights.collect { case Right(r) => r })
      case (lefts, _)    => Left(lefts.collect { case Left(l)    => l })
    }

  //TODO rename overloaded methods to fun1, fun2, fun3 or append TypeSignature instead of number?
  private def validateAllMethods(methods: List[(MethodSymbol, Type)]): List[Either[String, (MethodSymbol, Type)]] =
    methods
      .groupBy(m => methodPathPart(m._1))
      .map {
        case (_, x :: Nil) => Right(x)
        case (k, ms)       => Left(s"""method $k is overloaded (rename the method or add a @PathName("other-name"))""")
      }
      .toList

  private def findPathName(annotations: Seq[Annotation]) = annotations.reverse.map(_.tree).collectFirst {
    case Apply(Select(New(annotation), _), Literal(Constant(name)) :: Nil) if annotation.tpe =:= typeOf[PathName] => name.toString
  }

  def definedMethodsInType(tpe: Type): List[(MethodSymbol, Type)] =
    for {
      member <- tpe.members.toList
      if member.isAbstract
      if member.isMethod
      if member.isPublic
      if !member.isConstructor
      if !member.isSynthetic
      symbol = member.asMethod
    } yield (symbol, symbol.typeSignatureIn(tpe))

  def supportedMethodsInType(tpe: Type, expectedReturnType: Type): List[(MethodSymbol, Type)] = {
    val methods          = definedMethodsInType(tpe)
    val validatedMethods = methods.map { case (sym, tpe) => validateMethod(expectedReturnType, sym, tpe) }
    val validatedType = eitherSeq(validatedMethods)
      .flatMap(methods => eitherSeq(validateAllMethods(methods)))

    validatedType match {
      case Right(methods) => methods
      case Left(errors)   => abort(s"type '$tpe' contains unsupported methods: ${errors.mkString(", ")}")
    }
  }

  //TODO what about fqn for trait to not have overlaps?
  def traitPathPart(tpe: Type): String =
    findPathName(tpe.typeSymbol.annotations).getOrElse(tpe.typeSymbol.name.toString)

  def methodPathPart(m: MethodSymbol): String =
    findPathName(m.annotations).getOrElse(m.name.toString)

  def paramAsValDef(p: Symbol): ValDef             = q"val ${p.name.toTermName}: ${p.typeSignature}".asInstanceOf[ValDef]
  def paramsAsValDefs(m: Type): List[List[ValDef]] = m.paramLists.map(_.map(paramAsValDef))

  def paramsObjectName(path: List[String]) = s"${path.mkString("_")}"
  case class ParamsObject(tree: Tree, tpe: Tree)
  def paramsAsObject(tpe: Type, path: List[String]): ParamsObject = {
    val params   = tpe.paramLists.flatten
    val name     = paramsObjectName(path)
    val typeName = TypeName(name)

    params match {
      case Nil =>
        ParamsObject(
          tree = EmptyTree,
          tpe = tq"$rootPkg.Arguments.Empty.type"
        )
      case list =>
        ParamsObject(
          tree = q"final case class $typeName(..${list.map(paramAsValDef)})",
          tpe = tq"$typeName"
        )
    }
  }
  def objectToParams(tpe: Type, obj: TermName): List[List[Tree]] =
    tpe.paramLists.map(_.map(p => q"$obj.${p.name.toTermName}"))

  def newParamsObject(tpe: Type, path: List[String]): Tree = {
    val params   = tpe.paramLists.flatten
    val name     = paramsObjectName(path)
    val typeName = TypeName(name)

    params match {
      case Nil  => q"$rootPkg.Arguments.Empty"
      case list => q"""new $typeName(..${list.map(p => q"${p.name.toTermName}")})"""
    }
  }
}

object Translator {
  def apply[T](c: blackbox.Context)(f: Translator[c.type] => c.Tree): c.Expr[T] = {
    val tree = f(new Translator(c))
    c.Expr(tree)
  }
}

object RpcServiceMacro {
  def impl[API, F[_], Payload](c: blackbox.Context)(impl: c.Expr[API])(
      F: c.Expr[Sync[F]])(implicit apiTag: c.WeakTypeTag[API], payloadTag: c.WeakTypeTag[Payload], effectTag: c.WeakTypeTag[F[_]]): c.Expr[RpcService[F, Payload]] =
    Translator(c) { t =>
      import c.universe._

      val validMethods = t.supportedMethodsInType(apiTag.tpe, effectTag.tpe)

      val traitPathPart = t.traitPathPart(apiTag.tpe)
      val (methodTuples, paramsObjects) = validMethods.map {
        case (symbol, method) =>
          val methodPathPart              = t.methodPathPart(symbol)
          val path                        = traitPathPart :: methodPathPart :: Nil
          val paramsObject                = t.paramsAsObject(method, path)
          val argParams: List[List[Tree]] = t.objectToParams(method, TermName("args"))
          val innerReturnType             = method.finalResultType.typeArgs.head
          val payloadFunction =
            q"""(payload: ${payloadTag.tpe}) => impl.execute[${paramsObject.tpe}, $innerReturnType]($path, payload) { args =>
          value.${symbol.name.toTermName}(...$argParams)
        }"""

          (q"($methodPathPart, $payloadFunction)", paramsObject.tree)
      }.unzip

      q"""
      val value = $impl
      val impl = new ${t.rootInternalPkg}.RpcServiceImpl[${effectTag.tpe.typeConstructor}, ${payloadTag.tpe}]()($F)
      ..$paramsObjects
      ${c.prefix}.orElse($traitPathPart, scala.collection.mutable.HashMap(..$methodTuples))
    """
    }
}

object RpcClientMacro {
  def impl[API, F[_], Payload](c: blackbox.Context)(implicit apiTag: c.WeakTypeTag[API], effectTag: c.WeakTypeTag[F[_]]): c.Expr[API] = Translator(c) { t =>
    import c.universe._

    val validMethods = t.supportedMethodsInType(apiTag.tpe, effectTag.tpe)

    val traitPathPart = t.traitPathPart(apiTag.tpe)
    val (methodImplList, paramsObjects) = validMethods.collect {
      case (symbol, method) =>
        val methodPathPart  = t.methodPathPart(symbol)
        val path            = traitPathPart :: methodPathPart :: Nil
        val parameters      = t.paramsAsValDefs(method)
        val paramsObject    = t.paramsAsObject(method, path)
        val paramListValue  = t.newParamsObject(method, path)
        val innerReturnType = method.finalResultType.typeArgs.head

        (q"""
        override def ${symbol.name}(...$parameters): ${method.finalResultType} = {
          impl.execute[${paramsObject.tpe}, $innerReturnType]($path, $paramListValue)
        }
      """,
         paramsObject.tree)
    }.unzip
    val methodImpls = if (methodImplList.isEmpty) List(EmptyTree) else methodImplList

    q"""
      val impl = new ${t.rootInternalPkg}.RpcClientImpl(${c.prefix})
      ..$paramsObjects
      new ${apiTag.tpe.finalResultType} {
        ..$methodImpls
      }
    """
  }
}
