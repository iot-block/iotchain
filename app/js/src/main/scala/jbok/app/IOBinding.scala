package jbok.app

import cats.effect.IO
import com.thoughtworks.binding.{Binding, FutureBinding}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object IOBinding {
  def apply[A](io: IO[A]): Binding[Option[Try[A]]] =
    new FutureBinding(io.unsafeToFuture())
}
