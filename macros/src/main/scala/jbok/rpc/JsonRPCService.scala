package jbok.rpc

import cats.effect.IO
import cats.implicits._
import io.circe.generic.JsonCodec
import io.circe.parser._
import io.circe.syntax._
import jbok.rpc.JsonRPCService._
import jbok.rpc.json.JsonRPCResponse

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@JsonCodec
case class JsonRpcMethod(method: String)

class JsonRPCService(val handlers: Map[String, Handler] = Map.empty) {
  def mountAPI[API](api: API): JsonRPCService = macro JsonRPCServerMacro.mountAPI[API]

  def handle(json: String): IO[String] = {
    decode[JsonRpcMethod](json) match {
      case Left(e) => JsonRPCResponse.invalidRequest(e.toString).asJson.noSpaces.pure[IO]
      case Right(x) => handleReq(x.method, json)
    }
  }

  protected def handleReq(method: String, json: String): IO[String] =
    handlers.get(method) match {
      case Some(handler) => handler(json)
      case _ => IO.pure(JsonRPCResponse.invalidRequest(s"method ${method} does not exist").asJson.noSpaces)
    }
}

object JsonRPCService {
  def apply(): JsonRPCService = new JsonRPCService()

  type Handler = String => IO[String]
}

object JsonRPCServerMacro {
  def mountAPI[API: c.WeakTypeTag](c: blackbox.Context)(api: c.Expr[API]): c.Expr[JsonRPCService] = {
    import c.universe._

    val apiType: Type = weakTypeOf[API]

    val handlers = MacroUtils[c.type](c)
      .getApiMethods(apiType)
      .map((apiMember: MethodSymbol) => methodToHandler(c)(api, apiMember))

    val expr = c.Expr[JsonRPCService] {
      q"""
        new JsonRPCService(
            ${c.prefix.tree}.handlers ++ Map(..$handlers)
        )
       """
    }

    expr
  }

  private def methodToHandler[API](
      c: blackbox.Context)(api: c.Expr[API], method: c.universe.MethodSymbol): c.Expr[(String, Handler)] = {
    import c.universe._

    val macroUtils = MacroUtils[c.type](c)

    val fullMethodName = macroUtils.getMethodName(method)

    val parameterTypes: Iterable[Type] = method.asMethod.paramLists.flatten
      .map((param: Symbol) => param.typeSignature)

    val parameterType: Tree = macroUtils.getParameterType(method)

    def paramsAsTuple(params: TermName): Seq[Tree] = {
      Range(0, parameterTypes.size)
        .map(index => TermName(s"_${index + 1}"))
        .map(fieldName => q"$params.$fieldName")
    }

    val handler = c.Expr[Handler] {
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
          import _root_.io.circe.syntax._
          import _root_.io.circe.parser._
          import cats.implicits._
          import jbok.rpc.json._
          decode[JsonRPCRequest[$parameterType]](json) match {
            case Left(e) =>
              JsonRPCResponse.invalidRequest(e.toString()).asJson.noSpaces.pure[IO]
            case Right(req) =>
              val result = $run
              result.map(x => JsonRPCResponse.ok(req.id, x).asJson.noSpaces)
          }
         }
       """
    }

    c.Expr[(String, Handler)](q"""$fullMethodName -> $handler""")
  }
}
