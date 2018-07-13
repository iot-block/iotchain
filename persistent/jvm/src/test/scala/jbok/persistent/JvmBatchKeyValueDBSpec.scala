package jbok.persistent

import cats.effect.IO
import jbok.persistent.leveldb.{LevelDB, LevelDBConfig}

//class JvmBatchKeyValueDBSpec extends BatchKeyValueDBSpec {
//  check(BatchKeyValueDB.fromKV(KeyValueDB.inMemory[IO].unsafeRunSync()).unsafeRunSync())
//  check(BatchKeyValueDB.fromKV(LevelDB[IO](LevelDBConfig("testdb")).unsafeRunSync()).unsafeRunSync())
//}
