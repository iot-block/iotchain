package jbok.core

import cats.effect.IO
import jbok.persistent.KeyValueDB

trait HistoryFixture {
  lazy val history = mkHistory

  lazy val history2 = mkHistory

  lazy val history3 = mkHistory

  def mkHistory =
    (for {
      db      <- KeyValueDB.inMemory[IO]
      history <- History[IO](db)
      _       <- history.loadGenesis()
    } yield history).unsafeRunSync()
}
