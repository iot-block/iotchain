package jbok.network.rpc

import scala.reflect.macros.blackbox

object RpcClientMacro {
  def useAPI[API: c.WeakTypeTag](c: blackbox.Context): c.Expr[API] = {
    import c.universe._

    val returnType: Type = weakTypeOf[API]

    val members: List[c.Tree] = createMembers[c.type, API](c)

    val expr = c.Expr[API] {
      q"""
        new $returnType {
          import fs2._
          import _root_.io.circe.syntax._
          import _root_.io.circe.parser._
          import _root_.io.circe.generic.auto._
          import cats.effect.IO
          import cats.implicits._
          import jbok.network.json._
          import jbok.codec.json._

          ..$members
        }
      """
    }

    expr
  }

  private def createMembers[CONTEXT <: blackbox.Context, API: c.WeakTypeTag](c: CONTEXT): List[c.Tree] = {
    import c.universe._
    val apiType: Type = weakTypeOf[API]
    MacroUtils[c.type](c)
      .getMembers(apiType)
      .map((member: Symbol) => createMember[c.type, API](c)(member))
  }

  private def createMember[CONTEXT <: blackbox.Context, API: c.WeakTypeTag](c: CONTEXT)(
      member: c.universe.Symbol): c.Tree = {
    import c.universe._

    val macroUtils           = MacroUtils[c.type](c)
    val method               = member.asMethod
    val methodName: TermName = method.name

    val request = {
      val parameterLists      = macroUtils.getParameterLists(method)
      val parametersAsTuple   = macroUtils.getParametersAsTuple(method)
      val parameterType: Tree = macroUtils.getParameterType(method)
      val resultType: Type    = method.returnType.typeArgs.head

      java.util.UUID.randomUUID().toString

      val body =
        q"""
          val request =
            JsonRPCRequest[$parameterType](
              id = java.util.UUID.randomUUID().toString,
              method = ${methodName.toString},
              params = $parametersAsTuple
            )

          client.request(request.asJson.noSpaces).map(x => decode[JsonRPCResponse[$resultType]](x)).map {
            case Left(e) =>
              Left(JsonRPCResponse.parseError("parsing JsonRPCResponse failed"))
            case Right(x) => x match {
              case e: JsonRPCError => Left(e)
              case r: JsonRPCResult[${TypeName(resultType.toString)}] => Right(r.result)
            }
          }
       """

      q"""
        override def $methodName(...$parameterLists): Response[${resultType}] = {
          $body
        }
      """
    }

    def notification = {
      val resultType: Type = method.returnType
      val nestedType       = resultType.typeArgs(1)
      q"""
        override def $methodName: ${resultType} = {
          def enc(x: $nestedType): String = {
            val notification = JsonRPCNotification(${methodName.toString}, x)
            notification.asJson.noSpaces
          }

          def dec(s: String): $nestedType = {
            decode[JsonRPCNotification[$nestedType]](s).right.get.params
          }

          for {
            queue <- Stream.eval(${c.prefix.tree}.client.getOrCreateQueue(Some(${methodName.toString})))
            s <- queue.dequeue.imap[$nestedType](s => dec(s))(x => enc(x))
          } yield s
        }
      """
    }

    if (macroUtils.isRequest(c)(member)) {
      request
    } else {
      notification
    }
  }
}
