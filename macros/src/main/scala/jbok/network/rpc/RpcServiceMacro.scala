package jbok.network.rpc

import cats.effect.IO
import jbok.network.{Request, Response}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object RpcServiceMacro {
  def mountAPI[RpcService, API: c.WeakTypeTag](c: blackbox.Context)(api: c.Expr[API]): c.Expr[RpcService] = {
    import c.universe._

    val apiType = weakTypeOf[API]

    val macroUtils = MacroUtils[c.type](c)

    val handlers = macroUtils
      .getRequestMethods(apiType)
      .map((method: MethodSymbol) => {
        val methodName = method.name.toString
        val parameterTypes: Iterable[Type] = method.asMethod.paramLists.flatten
          .map((param: Symbol) => param.typeSignature)
        val parameterType: Tree = macroUtils.getParameterType(method)

        def paramsAsTuple(params: TermName): Seq[Tree] =
          Range(0, parameterTypes.size)
            .map(index => TermName(s"_${index + 1}"))
            .map(fieldName => q"$params.$fieldName")

        val handler = c.Expr[Request[IO] => IO[Response[IO]]] {
          val run = if (parameterTypes.isEmpty) {
            q"$api.$method"
          } else if (parameterTypes.size == 1) {
            q"$api.$method(body)"
          } else {
            val params = TermName("params")
            q"""
              val $params: $parameterType = body
              $api.$method(..${paramsAsTuple(params)})
            """
          }

          q"""
            (req: Request[IO]) => {
              req.bodyAsJson.unsafeRunSync().as[$parameterType] match {
                case Left(e) =>
                  IO.pure(Response.badRequest[IO](req.id))

                case Right(body) =>
                  $run.attempt.map {
                    case Left(e)  => Response.internalError[IO](req.id)
                    case Right(x) => Response.withJsonBody[IO](req.id, 200, "", x.asJson)
                  }
              }
            }
          """
        }

        c.Expr[(String, Request[IO] => IO[Response[IO]])](q"""$methodName -> $handler""")
      })

    val expr: c.Expr[RpcService] = c.Expr[RpcService] {
      q"""
        import jbok.network.{Request, Response}
        import jbok.codec.json.implicits._
        import _root_.io.circe.syntax._

        ${c.prefix.tree}.addHandlers($handlers)
       """
    }

    expr
  }
}
