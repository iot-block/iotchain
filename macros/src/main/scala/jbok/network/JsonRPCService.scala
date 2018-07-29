package jbok.network

import cats.effect.Effect
import cats.implicits._
import io.circe.generic.JsonCodec
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import fs2._
import fs2.async.mutable.Topic
import jbok.network.json.JsonRPCResponse

import scala.concurrent.ExecutionContext
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@JsonCodec
case class JsonRpcMethod(method: String)

@JsonCodec
case class JsonRpcId(id: String)

import jbok.network.JsonRPCService._

class JsonRPCService[F[_]](val handlers: Map[String, Handler[F]], val topics: Map[String, Pusher[F]])(
    implicit val F: Effect[F],
    EC: ExecutionContext) {
  type Service = JsonRPCService[F]

  type HandlerF = Handler[F]

  type TopicF = Topic[F, Option[String]]

  def mountAPI[API](api: API): JsonRPCService[F] = macro JsonRPCServerMacro.mountAPI[Service, HandlerF, TopicF, API]

  def pushEvents(maxQueued: Int): Stream[F, String] =
    Stream[Stream[F, String]](topics.values.toList.map(_.subscribe(maxQueued).unNone): _*).joinUnbounded

  def handle(json: String): F[String] =
    decode[JsonRpcMethod](json) match {
      case Left(e)  => JsonRPCResponse.invalidRequest(e.toString).asJson.noSpaces.pure[F]
      case Right(x) => handleReq(x.method, json)
    }

  protected def handleReq(method: String, json: String): F[String] =
    handlers.get(method) match {
      case Some(handler) => handler(json)
      case _ =>
        val idOpt = decode[JsonRpcId](json).toOption.map(_.id)
        F.pure(JsonRPCResponse.methodNotFound(method, idOpt).asJson.noSpaces)
    }
}

object JsonRPCService {
  def apply[F[_]: Effect](implicit EC: ExecutionContext): JsonRPCService[F] =
    new JsonRPCService[F](Map.empty, Map.empty)

  type Handler[F[_]] = String => F[String]

  type Pusher[F[_]] = Topic[F, Option[String]]
}

object JsonRPCServerMacro {
  def mountAPI[Service, HandlerF, PusherF, API: c.WeakTypeTag](c: blackbox.Context)(
      api: c.Expr[API]): c.Expr[Service] = {
    import c.universe._

    val apiType: Type = weakTypeOf[API]

    val handlers = MacroUtils[c.type](c)
      .getRequestMethods(apiType)
      .map((method: MethodSymbol) => {
        val macroUtils = MacroUtils[c.type](c)
        val fullMethodName = method.fullName.toLowerCase
        val parameterTypes: Iterable[Type] = method.asMethod.paramLists.flatten
          .map((param: Symbol) => param.typeSignature)
        val parameterType: Tree = macroUtils.getParameterType(method)

        def paramsAsTuple(params: TermName): Seq[Tree] =
          Range(0, parameterTypes.size)
            .map(index => TermName(s"_${index + 1}"))
            .map(fieldName => q"$params.$fieldName")

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
          import _root_.io.circe.generic.auto._
          import cats.implicits._
          import jbok.network.json._
          import jbok.codec.json._

          decode[JsonRPCRequest[$parameterType]](json) match {
            case Left(e) =>
              JsonRPCResponse.invalidRequest(e.toString()).asJson.noSpaces.pure[$effectType]
            case Right(req) =>
              val result = $run
              result.map {
                case Left(e) => e.copy(id = req.id).asJson.noSpaces
                case Right(x) => JsonRPCResponse.ok(req.id, x).asJson.noSpaces
              }
          }
         }
       """
        }

        c.Expr[(String, HandlerF)](q"""$fullMethodName -> $handler""")
      })

    val topics = MacroUtils[c.type](c)
      .getNotificationMethods(apiType)
      .map((member: Symbol) =>
        c.Expr[(String, PusherF)] {
          val fullName = member.asMethod.fullName.toLowerCase
          val resultType = member.asMethod.returnType.typeArgs(1)
          val nestedType = resultType.typeArgs.head

          q"""
            import _root_.io.circe.syntax._
            import _root_.io.circe.parser._
            import cats.implicits._
            import jbok.network.json._
            import fs2._

            def enc(x: $resultType): Option[String] = {
              x.map(s => {
                val notification = JsonRPCNotification(${fullName.toString}, s)
                notification.asJson.noSpaces
              })
            }

            def dec(s: Option[String]): $resultType = {
              s.map(x => decode[JsonRPCNotification[$nestedType]](x).right.get.params)
            }

            $fullName -> $api.$member.imap[Option[String]](x => enc(x))(s => dec(s))
         """
      })

    val expr = c.Expr[Service] {
      q"""
        new JsonRPCService(
          ${c.prefix.tree}.handlers ++ Map(..$handlers),
          ${c.prefix.tree}.topics ++ Map(..$topics)
        )
       """
    }

    expr
  }
}
