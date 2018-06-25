package jbok.rpc

import scala.reflect.macros.blackbox

class MacroUtils[CONTEXT <: blackbox.Context](val c: CONTEXT) {

  import c.universe._

  def getApiMethods(apiType: Type): Iterable[MethodSymbol] = {
    apiType.baseClasses
      .map(_.asClass)
      .filter(_.isTrait)
      .flatMap(
        _.typeSignature.decls
          .filter((apiMember: Symbol) => isJsonRpcMethod(c)(apiMember))
          .map((apiMember: Symbol) => apiMember.asMethod)
      )
  }

  def isJsonRpcMethod(c: blackbox.Context)(method: Symbol): Boolean = {
    method.isMethod && method.isPublic && !method.isConstructor
  }

  def getMethodName(method: MethodSymbol): String = {
    method.fullName
  }

  def getParameterType(method: MethodSymbol): Tree = {
    val parameterTypes: Iterable[Type] = method.asMethod.paramLists.flatten
      .map((param: Symbol) => param.typeSignature)

    tq"(..$parameterTypes)"
  }

  def getParameters(method: MethodSymbol): Seq[TermName] =
    method.paramLists.flatMap(parameterList => parameterList.map(parameter => parameter.asTerm.name))
}

object MacroUtils {
  def apply[CONTEXT <: blackbox.Context](c: CONTEXT) = new MacroUtils[CONTEXT](c)
}
