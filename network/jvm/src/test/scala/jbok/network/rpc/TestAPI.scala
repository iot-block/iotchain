package jbok.network.rpc

import cats.effect.IO
import io.circe.generic.JsonCodec

@JsonCodec
final case class Person(name: String, age: Int)

trait TestAPI[F[_]] {
  def foo: F[Int]

  def bar: F[String]

  def qux(name: String, age: Int): F[Person]

  def error: F[Unit]
}

class TestApiImpl extends TestAPI[IO] {
  override def foo: IO[Int] = IO.pure(42)

  override def bar: IO[String] = IO.pure("oho")

  override def qux(name: String, age: Int): IO[Person] = IO.pure(Person(name, age))

  override def error: IO[Unit] = IO.raiseError(new Exception("internal error"))
}
