package jbok.rpc

import fs2._
import jbok.rpc.json.JsonRPCMessage.RequestId

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

abstract class JsonRPCClient[F[_]] {
  def request(id: RequestId, json: String): F[String]

  def subscribe(maxQueued: Int): Stream[F, String]

  def start: F[Unit]

  def stop: F[Unit]

  def useAPI[API]: API = macro JsonRPCClientMacro.useAPI[API]
}

object JsonRPCClientMacro {
  def useAPI[API: c.WeakTypeTag](c: blackbox.Context): c.Expr[API] = {
    import c.universe._

    val apiType: Type = weakTypeOf[API]
    val methods: Iterable[c.Tree] = createMethods[c.type, API](c)

    val expr = c.Expr[API] {
      q"""
        new $apiType {
          import _root_.io.circe.syntax._
          import _root_.io.circe.parser._
          import cats.implicits._
          import jbok.rpc.json._
          ..$methods
        }
        """
    }

    expr
  }

  private def createMethods[CONTEXT <: blackbox.Context, API: c.WeakTypeTag](c: CONTEXT): Iterable[c.Tree] = {
    import c.universe._
    val apiType: Type = weakTypeOf[API]
    MacroUtils[c.type](c)
      .getApiMethods(apiType)
      .map((apiMethod: MethodSymbol) => createMethod[c.type, API](c)(apiMethod))
  }

  private def createMethod[CONTEXT <: blackbox.Context, API: c.WeakTypeTag](c: CONTEXT)(
      apiMethod: c.universe.MethodSymbol): c.Tree = {
    import c.universe._

    val macroUtils = MacroUtils[c.type](c)

    val methodName: TermName = apiMethod.name

    val fullMethodName: String = macroUtils.getMethodName(apiMethod)

    val parameterLists: List[List[Tree]] =
      apiMethod.paramLists.map((parameterList: List[Symbol]) => {
        parameterList.map((parameter: Symbol) => {
          q"${parameter.name.toTermName}: ${parameter.typeSignature}"
        })
      })

    val parameters: Seq[TermName] = apiMethod.paramLists.flatMap(parameterList => {
      parameterList.map(parameter => {
        parameter.asTerm.name
      })
    })

    val parametersAsTuple = if (parameters.size == 1) {
      val parameter = parameters.head
      q"$parameter"
    } else {
      q"(..$parameters)"
    }

    val parameterType: Tree = macroUtils.getParameterType(apiMethod)

    val returnType: Type = apiMethod.returnType

    val resultType: Type = returnType.typeArgs.head

    def methodBody = c.Expr[returnType.type] {
      q"""
        val requestId = jbok.rpc.json.RequestId.random

        val request =
          JsonRPCRequest[$parameterType](
            id = requestId,
            method = ${fullMethodName.toString},
            params = $parametersAsTuple
          )

        ${c.prefix.tree}.request(request.id, request.asJson.noSpaces).map(x => decode[JsonRPCResponse[$resultType]](x)).flatMap {
          case Left(e) =>
            IO.raiseError[$resultType](new Exception(e))
          case Right(x) => x match {
            case e: JsonRPCError => IO.raiseError[$resultType](new Exception(e.error.toString()))
            case r: JsonRPCResult[${TypeName(resultType.toString)}] => r.result.pure[IO]
          }
        }
       """
    }

    q"""
      override def $methodName(...$parameterLists): $returnType = {
        $methodBody
      }
     """
  }
}
