package jbok.common

import java.util.UUID

import jbok.common.TypedMap._

final class TypedMap private (private val m: Map[UUID, Box]) {
  def get[A](key: Key[A]): Option[A] = m.get(key.id).flatMap(_.get(key))

  def put[A](key: Key[A], a: A): TypedMap = new TypedMap(m + (key.id -> Box(key, a)))

  def del[A](key: Key[A]): TypedMap = new TypedMap(m - key.id)

  def +[A](kv: (Key[A], A)): TypedMap = put(kv._1, kv._2)

  def ++(that: TypedMap) = new TypedMap(m ++ that.m)
}

object TypedMap {
  def empty = new TypedMap(Map.empty)

  final class Key[A] private (val id: UUID)

  object Key {
    def apply[A](id: UUID) = new Key[A](id)
  }

  final class Box private (private val id: UUID, val a: Any) {
    def get[A](key: Key[A]): Option[A] =
      if (key.id == id) Some(a.asInstanceOf[A])
      else None
  }

  object Box {
    def apply[A](key: Key[A], a: A): Box = new Box(key.id, a.asInstanceOf[Any])
  }
}
