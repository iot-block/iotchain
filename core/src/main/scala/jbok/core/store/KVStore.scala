package jbok.core.store

import cats.Id

import scala.collection.mutable

trait KVStore[F[_], Key, Val] {
  def get(key: Key): F[Val]
  def getOpt(key: Key): F[Option[Val]]
  def put(key: Key, newVal: Val): F[Unit]
  def del(key: Key): F[Unit]
  def has(key: Key): F[Boolean]
  def keys: F[List[Key]]
  def clear(): F[Unit]
}

object KVStore {
  implicit def apply[Key, Val] = new KVStore[Id, Key, Val] {
    private val m = mutable.Map[Key, Val]()

    override def get(key: Key): Id[Val] = m(key)

    override def getOpt(key: Key): Id[Option[Val]] = m.get(key)

    override def put(key: Key, newVal: Val): Id[Unit] = m.put(key, newVal)

    override def del(key: Key): Id[Unit] = m.remove(key)

    override def has(key: Key): Id[Boolean] = m.contains(key)

    override def keys: Id[List[Key]] = m.keys.toList

    override def clear(): Id[Unit] = m.clear()
  }
}
