package jbok.common.log

import scribe.{Loggable, Position}

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macros to expand")
object Macros {
  def getLevel(c: blackbox.Context): c.Tree = {
    import c.universe._

    c.macroApplication.symbol.name.decodedName.toString match {
      case "t" | "trace" => q"_root_.scribe.Level.Trace"
      case "d" | "debug" => q"_root_.scribe.Level.Debug"
      case "i" | "info" => q"_root_.scribe.Level.Info"
      case "w" | "warn" => q"_root_.scribe.Level.Warn"
      case "e" | "error" => q"_root_.scribe.Level.Error"
    }
  }

  def autoLevel0[F[_]](c: blackbox.Context)
                      (): c.Tree = {
    import c.universe._

    val level = getLevel(c)
    log[F, String](c)(level, q"""""""", reify[Option[Throwable]](None))(c.Expr[Loggable[String]](q"scribe.Loggable.StringLoggable"))
  }

  def autoLevel1[F[_]](c: blackbox.Context)
                   (message: c.Tree)
                   (implicit f: c.WeakTypeTag[F[_]]): c.Tree = {
    import c.universe._
    val level = getLevel(c)
    log[F, String](c)(level, message, reify[Option[Throwable]](None))(c.Expr[Loggable[String]](q"scribe.Loggable.StringLoggable"))
  }

  def autoLevel2[F[_]](c: blackbox.Context)
                      (message: c.Tree, t: c.Expr[Throwable])
                      (implicit f: c.WeakTypeTag[F[_]]): c.Tree = {
    import c.universe._

    val level = getLevel(c)
    log[F, String](c)(level, message, c.Expr[Option[Throwable]](q"Option($t)"))(c.Expr[Loggable[String]](q"scribe.Loggable.StringLoggable"))
  }

  def log[F[_], M](c: blackbox.Context)
            (level: c.Tree,
             message: c.Tree,
             throwable: c.Expr[Option[Throwable]])
            (loggable: c.Expr[Loggable[M]])
            (implicit f: c.WeakTypeTag[F[_]], m: c.WeakTypeTag[M]): c.Tree = {
    import c.universe._

    val logger = c.prefix.tree
    val p = position(c)
    val messageFunction = c.typecheck(q"() => $message")
    c.internal.changeOwner(message, c.internal.enclosingOwner, messageFunction.symbol)

    q"""
        _root_.cats.effect.Sync[${f.tpe.typeConstructor}].delay {
         _root_.scribe.log(_root_.scribe.LogRecord[$m](
          level = $level,
          value = $level.value,
          messageFunction = $messageFunction,
          loggable = $loggable,
          throwable = $throwable,
          fileName = ${p.fileName},
          className = ${p.className},
          methodName = ${p.methodName},
          line = ${p.line},
          column = ${p.column}
         ))
       }
     """
  }

  def pushPosition(c: blackbox.Context)(): c.Expr[Unit] = {
    import c.universe._

    val p = position(c)
    Position.push(p)
    reify(())
  }

  def position(c: blackbox.Context): Position = {
    val EnclosingType(className, methodName) = enclosingType(c)
    val line = c.enclosingPosition.line match {
      case -1 => None
      case n => Some(n)
    }
    val column = c.enclosingPosition.column match {
      case -1 => None
      case n => Some(n)
    }
    val fileName = c.enclosingPosition.source.path
    Position(className, methodName, line, column, fileName)
  }

  def enclosingType(c: blackbox.Context): EnclosingType = {
    val term = c.internal.enclosingOwner.asTerm match {
      case t if t.isMethod => t
      case t if t.owner.isMethod => t.owner
      case t => t
    }
    val className = term.owner.fullName
    val methodName = if (term.isMethod) Some(term.asMethod.name.decodedName.toString) else None
    EnclosingType(className, methodName)
  }

  final case class EnclosingType(className: String, methodName: Option[String])
}
