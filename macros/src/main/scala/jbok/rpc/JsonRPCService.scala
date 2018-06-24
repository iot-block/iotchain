package jbok.rpc

import cats.effect.Effect
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

class JsonRPCService[F[_]](val handlers: Map[String, Handler[F]])(implicit val F: Effect[F]) {
  type Service = JsonRPCService[F]

  type HandlerF = Handler[F]

  def mountAPI[API](api: API): Service = macro JsonRPCServerMacro.mountAPI[Service, HandlerF, API]

  def handle(json: String): F[String] = {
    decode[JsonRpcMethod](json) match {
      case Left(e) => JsonRPCResponse.invalidRequest(e.toString).asJson.noSpaces.pure[F]
      case Right(x) => handleReq(x.method, json)
    }
  }

  protected def handleReq(method: String, json: String): F[String] =
    handlers.get(method) match {
      case Some(handler) => handler(json)
      case _ => F.pure(JsonRPCResponse.invalidRequest(s"method ${method} does not exist").asJson.noSpaces)
    }
}

object JsonRPCService {
  def apply[F[_]: Effect]: JsonRPCService[F] = new JsonRPCService[F](Map.empty)

  type Handler[F[_]] = String => F[String]
}

object JsonRPCServerMacro {
  def mountAPI[Service, HandlerF, API: c.WeakTypeTag](c: blackbox.Context)(api: c.Expr[API]): c.Expr[Service] = {
    import c.universe._

    val apiType: Type = weakTypeOf[API]

    val handlers = MacroUtils[c.type](c)
      .getApiMethods(apiType)
      .map((apiMember: MethodSymbol) => methodToHandler[HandlerF, API](c)(api, apiMember))


    val expr = c.Expr[Service] {
      q"""
        new JsonRPCService(
            ${c.prefix.tree}.handlers ++ Map(..$handlers)
        )
       """
    }

    expr
  }

  private def methodToHandler[HandlerF, API: c.WeakTypeTag](
      c: blackbox.Context)(api: c.Expr[API], method: c.universe.MethodSymbol): c.Expr[(String, HandlerF)] = {
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

    val effectType = weakTypeOf[API].typeArgs.head

    val handler = c.Expr[HandlerF] {
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
              JsonRPCResponse.invalidRequest(e.toString()).asJson.noSpaces.pure[$effectType]
            case Right(req) =>
              val result = $run
              result.map(x => JsonRPCResponse.ok(req.id, x).asJson.noSpaces)
          }
         }
       """
    }

    c.Expr[(String, HandlerF)](q"""$fullMethodName -> $handler""")
  }
}
