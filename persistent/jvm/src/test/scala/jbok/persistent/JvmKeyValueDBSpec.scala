package jbok.persistent

import cats.effect.IO
import jbok.persistent.leveldb.{LevelDB, LevelDBConfig}

class JvmKeyValueDBSpec extends KeyValueDBSpec {
  check(KeyValueDB.inMemory[IO].unsafeRunSync())
  check(LevelDB[IO](LevelDBConfig("testdb")).unsafeRunSync())
}
