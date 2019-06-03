package jbok.persistent

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2._
import jbok.common.testkit._
import jbok.persistent.testkit._
import scodec.bits.ByteVector
import jbok.common.CommonArb._

object RocksKVStoreSpec extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    testRocksKVStore().use { store =>
      // if we do not close the batch object after each writeBatch, we will get RocksDBException: unknown WriteBatch tag
      val kvs = Stream.repeatEval(IO(List.fill(100)(random[ByteVector])))
      val s = kvs
        .map { xs =>
          Stream.eval(store.writeBatch(ColumnFamily.default, xs.map(x => x -> x), Nil))
        }
        .parJoin(8)

      s.compile.drain.as(ExitCode.Success)
    }
  }
}
