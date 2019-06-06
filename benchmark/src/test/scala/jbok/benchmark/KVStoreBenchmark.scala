package jbok.benchmark

import cats.effect.IO
import org.openjdk.jmh.annotations.{Benchmark, OperationsPerInvocation, TearDown}
import org.scalacheck.Gen
import cats.implicits._
import jbok.common.{gen, FileUtil}
import jbok.core.StatelessGen
import jbok.persistent.{ColumnFamily, MemoryKVStore}
import jbok.persistent.rocksdb.RocksKVStore

class KVStoreBenchmark extends JbokBenchmark {
  val size = 10000

  val blockHeaders = Gen.listOfN(size, StatelessGen.blockHeader).sample.get.toArray

  var i = 0

  val keyGen   = gen.sizedByteArray(16)
  val valueGen = gen.sizedByteArray(100)

  val keys   = Gen.listOfN(size, keyGen).sample.get
  val values = Gen.listOfN(size, valueGen).sample.get
  val kvs    = keys.zip(values).toArray

  val dirRocks         = FileUtil[IO].temporaryDir().allocated.unsafeRunSync()._1
  val (dbRocks, close) = RocksKVStore.resource[IO](dirRocks.path, List(ColumnFamily.default)).allocated.unsafeRunSync()
  var dbMem            = MemoryKVStore[IO].unsafeRunSync()

  @Benchmark
  def putRocks() = {
    val (k, v) = kvs(i)
    i = (i + 1) % kvs.length
    dbRocks.put(ColumnFamily.default, k, v).unsafeRunSync()
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def putBatchRocks() = {
    i = (i + 1) % (kvs.length / 100)
    val put = (i * 100 until (i + 1) * 100)
      .map(i => kvs(i))
      .toList
    dbRocks.writeBatch(ColumnFamily.default, put, Nil).unsafeRunSync()
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def putBatchRocksPar() = {
    i = (i + 1) % (kvs.length / 100)
    val put = (i * 100 until (i + 1) * 100)
      .map(i => kvs(i))
      .toList
    put.parTraverse { case (k, v) => dbRocks.put(ColumnFamily.default, k, v) }.unsafeRunSync()
  }

  @TearDown
  def tearDown(): Unit = {
    close.unsafeRunSync()
    dbMem = MemoryKVStore[IO].unsafeRunSync()
    RocksKVStore.destroy[IO](dirRocks.path).unsafeRunSync()
  }
}
