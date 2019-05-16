package jbok.common.log

import scribe.{Loggable, Position}

import scala.annotation.compileTimeOnly
import scala.concurrent.ExecutionContext
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
        _root_.cats.Monad[${f.tpe.typeConstructor}].pure {
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

  def executionContext(c: blackbox.Context): c.Expr[ExecutionContext] = {
    import c.universe._

    executionContextCustom(c)(c.Expr[ExecutionContext](q"_root_.scala.concurrent.ExecutionContext.global"))
  }

  def executionContextCustom(c: blackbox.Context)(context: c.Expr[ExecutionContext]): c.Expr[ExecutionContext] = {
    import c.universe._

    implicit val liftablePosition: c.universe.Liftable[scribe.Position] = new Liftable[scribe.Position] {
      override def apply(p: scribe.Position): c.universe.Tree = q"scribe.Position(${p.className}, ${p.methodName}, ${p.line}, ${p.column}, ${p.fileName})"
    }

    Position.push(position(c))
    val stack = Position.stack
    try {
      c.Expr[ExecutionContext](
        q"""
          new scribe.LoggingExecutionContext($context, List(..$stack))
       """)
    } finally {
      Position.pop()
    }
  }

  def async[Return](c: blackbox.Context)(f: c.Tree)(implicit r: c.WeakTypeTag[Return]): c.Tree = {
    import c.universe._

    val function = c.typecheck(q"() => $f")
    c.internal.changeOwner(f, c.internal.enclosingOwner, function.symbol)
    q"""
       try {
         $function() match {
           case future: Future[_] => future.recover({
             case throwable: Throwable => throw scribe.Position.fix(throwable)
           })(scribe.Execution.global).asInstanceOf[$r]
           case result => result
         }
       } catch {
         case t: Throwable => throw scribe.Position.fix(t)
       }
     """
  }

  def future[Return](c: blackbox.Context)(f: c.Tree): c.Tree = {
    import c.universe._

    val context = executionContext(c)
    val function = c.typecheck(q"() => $f")
    c.internal.changeOwner(f, c.internal.enclosingOwner, function.symbol)
    q"""
       import _root_.scala.concurrent.Future
       implicit def executionContext: _root_.scala.concurrent.ExecutionContext = $context
       val future = Future($function())(executionContext)
       future.recover({
         case throwable: Throwable => throw scribe.Position.fix(throwable)
       })(executionContext)
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
