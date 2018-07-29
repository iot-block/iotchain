package jbok.network

import cats.effect.Sync
import fs2._
import fs2.async.mutable.Topic
import jbok.network.json.JsonRPCMessage.RequestId

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

abstract class JsonRPCClient[F[_]](implicit val F: Sync[F]) {
  def request(id: RequestId, json: String): F[String]

  def getOrCreateTopic(method: String): F[Topic[F, Option[String]]]

  def start: F[Unit]

  def stop: F[Unit]

  def useAPI[API]: API = macro JsonRPCClientMacro.useAPI[API]
}

object JsonRPCClientMacro {
  def useAPI[API: c.WeakTypeTag](c: blackbox.Context): c.Expr[API] = {
    import c.universe._

    val returnType: Type = weakTypeOf[API]

    val members: List[c.Tree] = createMembers[c.type, API](c)

    val expr = c.Expr[API] {
      q"""
        new $returnType {
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

    val macroUtils = MacroUtils[c.type](c)
    val method = member.asMethod
    val methodName: TermName = method.name
    val fullName: String = method.fullName.toLowerCase

    val request = {
      val parameterLists = macroUtils.getParameterLists(method)
      val parametersAsTuple = macroUtils.getParametersAsTuple(method)
      val parameterType: Tree = macroUtils.getParameterType(method)
      val resultType: Type = method.returnType.typeArgs.head

      val body =
        q"""
          val requestId = jbok.network.json.RequestId.random

          val request =
            JsonRPCRequest[$parameterType](
              id = requestId,
              method = ${fullName.toString},
              params = $parametersAsTuple
            )

          ${c.prefix.tree}.request(request.id, request.asJson.noSpaces).map(x => decode[JsonRPCResponse[$resultType]](x)).map {
            case Left(e) =>
              Left(JsonRPCResponse.parseError("parsing JsonRPCResponse failed"))
            case Right(x) => x match {
              case e: JsonRPCError => Left(e)
              case r: JsonRPCResult[${TypeName(resultType.toString)}] => Right(r.result)
            }
          }
       """

      q"""
        override def $methodName(...$parameterLists) = {
          $body
        }
      """
    }

    def notification = {
      val resultType: Type = method.returnType.typeArgs(1)
      val nestedType = resultType.typeArgs.head
      q"""
        override val $methodName = {
          def enc(x: $resultType): Option[String] = {
            x.map(s => {
              val notification = JsonRPCNotification(${fullName.toString}, s)
              notification.asJson.noSpaces
            })
          }

          def dec(s: Option[String]): $resultType = {
            s.map(x => decode[JsonRPCNotification[$nestedType]](x).right.get.params)
          }

          ${c.prefix.tree}.getOrCreateTopic(${fullName.toString}).unsafeRunSync.imap[$resultType](s => dec(s))(x => enc(x))
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
