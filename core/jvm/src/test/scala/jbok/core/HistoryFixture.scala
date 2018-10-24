package jbok.core

import cats.effect.IO
import jbok.persistent.KeyValueDB

trait HistoryFixture {
  val db      = KeyValueDB.inMemory[IO].unsafeRunSync()
  val history = History[IO](db).unsafeRunSync()
  history.loadGenesisBlock().unsafeRunSync()
}
