package jbok.network.rpc

import cats.effect.IO
import io.circe.generic.JsonCodec
import jbok.network.json.JsonRPCResponse
import fs2._

@JsonCodec
case class Person(name: String, age: Int)

trait TestAPI extends RpcAPI {
  def foo: Response[Int]

  def bar: Response[String]

  def qux(name: String, age: Int): Response[Person]

  def error: Response[Unit]

  def events: Stream[IO, Int]
}

class TestApiImpl extends TestAPI {
  override def foo: Response[Int] = IO.pure(Right(42))

  override def bar: Response[String] = IO.pure(Right("oho"))

  override def qux(name: String, age: Int): Response[Person] = IO.pure(Right(Person(name, age)))

  override def error: Response[Unit] = IO.pure(Left(JsonRPCResponse.internalError("error")))

  override def events: Stream[IO, Int] = Stream(1 to 1000: _*).covary[IO]
}
