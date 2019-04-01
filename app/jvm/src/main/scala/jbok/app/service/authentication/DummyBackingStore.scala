package jbok.app.service.authentication

import cats.data.OptionT
import cats.effect.IO
import tsec.authentication.BackingStore

import scala.collection.mutable

object DummyBackingStore {
  def apply[I, V](getId: V => I): BackingStore[IO, I, V] = new BackingStore[IO, I, V] {
    private val storageMap = mutable.HashMap.empty[I, V]

    def put(elem: V): IO[V] = {
      val map = storageMap.put(getId(elem), elem)
      if (map.isEmpty)
        IO.pure(elem)
      else
        IO.raiseError(new IllegalArgumentException)
    }

    def get(id: I): OptionT[IO, V] =
      OptionT.fromOption[IO](storageMap.get(id))

    def update(v: V): IO[V] = {
      storageMap.update(getId(v), v)
      IO.pure(v)
    }

    def delete(id: I): IO[Unit] =
      storageMap.remove(id) match {
        case Some(_) => IO.unit
        case None    => IO.raiseError(new IllegalArgumentException)
      }
  }
}
