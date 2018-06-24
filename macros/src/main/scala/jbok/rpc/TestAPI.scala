package jbok.rpc

import cats.effect.IO
import io.circe.generic.JsonCodec

@JsonCodec
case class Person(age: Int, name: String)

trait TestAPI[F[_]] {
  def foo: F[Int]

  def bar: F[String]

  def grow(age: Int, name: String): F[Person]
}

object TestAPI {
  val apiImpl = new TestAPI[IO] {
    override def foo: IO[Int] = IO(42)

    override def bar: IO[String] = IO("oho")

    override def grow(age: Int, name: String): IO[Person] = IO(Person(age + 1, name))
  }
}
