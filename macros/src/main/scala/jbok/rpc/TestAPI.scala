package jbok.rpc

import cats.effect.IO
import io.circe.generic.JsonCodec

@JsonCodec
case class Person(age: Int, name: String)

trait TestAPI {
  def foo: IO[Int] = IO(42)

  def bar: IO[String] = IO("qux")

  def grow(age: Int, name: String): IO[Person] = IO(Person(age, name))
}

