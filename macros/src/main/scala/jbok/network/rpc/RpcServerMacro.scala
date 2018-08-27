package jbok.network.rpc

import cats.effect.IO

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object RpcServerMacro {
  def mountAPI[RpcServer, API: c.WeakTypeTag](c: blackbox.Context)(api: c.Expr[API]): c.Expr[RpcServer] = {
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

        val handler = c.Expr[String => IO[String]] {
          val run = if (parameterTypes.isEmpty) {
            q"$api.$method"
          } else if (parameterTypes.size == 1) {
            q"$api.$method(req.params)"
          } else {
            val params = TermName("params")
            q"""
              val $params: $parameterType = req.params
              $api.$method(..${paramsAsTuple(params)})
            """
          }

          q"""
            (json: String) => {
              decode[JsonRPCRequest[$parameterType]](json) match {
                case Left(e) =>
                  IO.pure(JsonRPCResponse.invalidRequest(e.toString()).asJson.noSpaces)
                case Right(req) =>
                  $run.map {
                    case Left(e) => e.copy(id = req.id).asJson.noSpaces
                    case Right(x) => JsonRPCResponse.ok(req.id, x).asJson.noSpaces
                  }
              }
            }
          """
        }

        c.Expr[(String, String => IO[String])](q"""$methodName -> $handler""")
      })

    val expr = c.Expr[RpcServer] {
      q"""
        import fs2._
        import _root_.io.circe.syntax._
        import _root_.io.circe.parser._
        import _root_.io.circe.generic.auto._
        import cats.implicits._
        import jbok.network.json._
        import jbok.codec.json._
        new RpcServer(${c.prefix.tree}.handlers ++ Map(..$handlers), ${c.prefix.tree}.queue)
       """
    }

    expr
  }
}
