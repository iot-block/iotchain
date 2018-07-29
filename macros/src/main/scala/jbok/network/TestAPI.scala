package jbok.network

import cats.effect.Sync
import fs2.async.mutable.Topic
import io.circe.generic.JsonCodec

@JsonCodec
case class Person(age: Int, name: String)

trait TestAPI[F[_]] extends JsonRpcAPI[F] {
  def foo: R[Int]

  def bar: R[String]

  def grow(age: Int, name: String): R[Person]

  val events: Topic[F, Option[String]]
}

class TestApiImpl[F[_]](val events: Topic[F, Option[String]])(implicit F: Sync[F]) extends TestAPI[F] {
  override def foo: R[Int] = F.pure(Right(42))

  override def bar: R[String] = F.pure(Right("oho"))

  override def grow(age: Int, name: String): R[Person] = F.pure(Right(Person(age + 1, name)))
}
