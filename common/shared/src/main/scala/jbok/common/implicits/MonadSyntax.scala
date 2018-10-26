package jbok.common.implicits

import cats.implicits._
import cats.{Functor, Monad}

trait MonadSyntax {
  implicit final def booleanSyntax[F[_]](fa: F[Boolean]): MonadBooleanOps[F] = new MonadBooleanOps(fa)
}

final class MonadBooleanOps[F[_]](val a: F[Boolean]) extends AnyVal {
  def &&(b: => F[Boolean])(implicit M: Monad[F]): F[Boolean] = {
    M.ifM(a)(b, M.pure(false))
  }

  def ||(b: => F[Boolean])(implicit M: Monad[F]): F[Boolean] = {
    M.ifM(a)(M.pure(true), b)
  }

  def unary_!(implicit F: Functor[F]): F[Boolean] = {
    a.map(!_)
  }
}
