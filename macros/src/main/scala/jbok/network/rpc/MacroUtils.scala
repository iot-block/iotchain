package jbok.network.rpc

import scala.reflect.macros.blackbox

class MacroUtils[CONTEXT <: blackbox.Context](val c: CONTEXT) {

  import c.universe._

  def getMembers(apiType: Type): List[Symbol] =
    apiType.baseClasses
      .map(_.asClass)
      .filter(_.isTrait)
      .flatMap(
        _.typeSignature.decls
          .filter((member: Symbol) => isJsonRPCMember(c)(member))
      )

  def getRequestMethods(apiType: Type): List[MethodSymbol] =
    getMembers(apiType).filter(m => !isNotification(c)(m)).map(_.asMethod)

  def getNotificationMethods(apiType: Type): List[MethodSymbol] =
    getMembers(apiType).filter(m => isNotification(c)(m)).map(_.asMethod)

  def isJsonRPCMember(c: blackbox.Context)(method: Symbol): Boolean =
    method.isMethod && method.isPublic && !method.isConstructor

  def isRequest(c: blackbox.Context)(member: Symbol): Boolean =
    !isNotification(c)(member)

  def isNotification(c: blackbox.Context)(member: Symbol): Boolean =
    member.asMethod.returnType.toString.contains("fs2")

  def getParameterType(method: MethodSymbol): Tree = {
    val parameterTypes: List[Type] = method.asMethod.paramLists.flatten
      .map((param: Symbol) => param.typeSignature)

    tq"(..$parameterTypes)"
  }

  def getParameterLists(method: MethodSymbol): List[List[Tree]] =
    method.paramLists.map((parameterList: List[Symbol]) => {
      parameterList.map((parameter: Symbol) => {
        q"${parameter.name.toTermName}: ${parameter.typeSignature}"
      })
    })

  def getParameters(method: MethodSymbol): Seq[TermName] =
    method.paramLists.flatMap(parameterList => parameterList.map(parameter => parameter.asTerm.name))

  def getParametersAsTuple(method: MethodSymbol) = {
    val parameters = getParameters(method)
    if (parameters.size == 1) {
      q"${parameters.head}"
    } else {
      q"(..$parameters)"
    }
  }
}

object MacroUtils {
  def apply[CONTEXT <: blackbox.Context](c: CONTEXT) = new MacroUtils[CONTEXT](c)
}
