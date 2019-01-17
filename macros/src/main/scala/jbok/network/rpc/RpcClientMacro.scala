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
          import cats.effect.IO
          import _root_.io.circe.syntax._
          import jbok.codec.json.implicits._
          import jbok.codec.rlp.implicits._
          import jbok.network.{Request}

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
      val resultType: Type = method.returnType.typeArgs match {
        case head :: _ => head
        case _         => throw new Exception("resultType must have nonEmpty typeArgs")
      }

      q"""
        override def $methodName(...$parameterLists): IO[${resultType}] = {
          val request: Request[IO] =
            Request.withJsonBody[IO](
              id = java.util.UUID.randomUUID(),
              method = ${methodName.toString},
              body = $parametersAsTuple.asJson
            )

          val p = for {
            resp   <- ${c.prefix.tree}.request(request)
            json   <- resp.bodyAsJson
            result <- IO.fromEither(json.as[$resultType])
          } yield result

          p.attempt.flatMap {
            case Left(e)     => IO.raiseError(e)
            case Right(resp) => IO.pure(resp)
          }
        }
      """
    }
    request
  }
}
