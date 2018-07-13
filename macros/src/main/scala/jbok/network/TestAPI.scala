package jbok.network

import cats.effect.IO
import fs2.async.mutable.{Queue, Topic}
import io.circe.generic.JsonCodec

import scala.concurrent.ExecutionContext.Implicits.global

@JsonCodec
case class Person(age: Int, name: String)

trait TestAPI[F[_]] {
  def foo: F[Int]

  def bar: F[String]

  def grow(age: Int, name: String): F[Person]

  val events: Topic[F, Option[String]]
}

object TestAPI {
  val apiImpl = new TestAPI[IO] {
    override def foo: IO[Int] = IO(42)

    override def bar: IO[String] = IO("oho")

    override def grow(age: Int, name: String): IO[Person] = IO(Person(age + 1, name))

    override val events: Topic[IO, Option[String]] = fs2.async.topic[IO, Option[String]](None).unsafeRunSync()
  }
}
