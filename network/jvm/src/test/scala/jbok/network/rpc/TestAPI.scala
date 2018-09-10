package jbok.network.rpc

import cats.effect.IO
import io.circe.generic.JsonCodec
import jbok.network.json.JsonRPCResponse
import fs2._

@JsonCodec
case class Person(name: String, age: Int)

trait TestAPI {
  def foo: IO[Int]

  def bar: IO[String]

  def qux(name: String, age: Int): IO[Person]

  def error: IO[Unit]

  def events: Stream[IO, Int]
}

class TestApiImpl extends TestAPI {
  override def foo: IO[Int] = IO.pure(42)

  override def bar: IO[String] = IO.pure("oho")

  override def qux(name: String, age: Int): IO[Person] = IO.pure(Person(name, age))

  override def error: IO[Unit] = IO.raiseError(JsonRPCResponse.internalError("error"))

  override def events: Stream[IO, Int] = Stream(1 to 1000: _*).covary[IO]
}
