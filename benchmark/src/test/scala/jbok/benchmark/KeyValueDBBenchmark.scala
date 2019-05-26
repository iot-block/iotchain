package jbok.benchmark

import cats.effect.IO
import jbok.common.testkit._
import jbok.core.testkit._
import org.openjdk.jmh.annotations.{Benchmark, OperationsPerInvocation, TearDown}
import org.scalacheck.Gen
import cats.implicits._
import jbok.common.FileUtil
import jbok.persistent.{ColumnFamily, MemoryKVStore}
import jbok.persistent.rocksdb.RocksKVStore

class KeyValueDBBenchmark extends JbokBenchmark {
  val size = 10000

  val blockHeaders = Gen.listOfN(size, arbBlockHeader.arbitrary).sample.get.toArray

  var i = 0

  val keyGen   = genBoundedByteVector(16, 16)
  val valueGen = genBoundedByteVector(100, 100)

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
    RocksKVStore.destroy[IO](dirRocks.path).unsafeToFuture()
  }
}
