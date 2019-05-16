package jbok.benchmark

import better.files._
import cats.effect.IO
import jbok.persistent.KeyValueDB
import jbok.common.testkit._
import jbok.core.testkit._
import jbok.persistent.rocksdb.RocksDB
import org.openjdk.jmh.annotations.{Benchmark, OperationsPerInvocation, TearDown}
import org.scalacheck.Gen
import cats.implicits._

class KeyValueDBBenchmark extends JbokBenchmark {
  val size = 10000

  val blockHeaders = Gen.listOfN(size, arbBlockHeader.arbitrary).sample.get.toArray

  var i = 0

  val keyGen   = genBoundedByteVector(16, 16)
  val valueGen = genBoundedByteVector(100, 100)

  val keys   = Gen.listOfN(size, keyGen).sample.get
  val values = Gen.listOfN(size, valueGen).sample.get
  val kvs    = keys.zip(values).toArray

  val dirIq80  = File.newTemporaryDirectory()
  val dirJni   = File.newTemporaryDirectory()
  val dirRocks = File.newTemporaryDirectory()

  val (dbRocks, close) = RocksDB.resource[IO](dirRocks.path).allocated.unsafeRunSync()
  var dbMem            = KeyValueDB.inmem[IO].unsafeRunSync()

  @Benchmark
  def putRocks() = {
    val (k, v) = kvs(i)
    i = (i + 1) % kvs.length
    dbRocks.putRaw(k, v).unsafeRunSync()
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def putBatchRocks() = {
    i = (i + 1) % (kvs.length / 100)
    val put = (i * 100 until (i + 1) * 100)
      .map(i => kvs(i))
      .toList
    dbRocks.writeBatchRaw(put, Nil).unsafeRunSync()
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def putBatchRocksPar() = {
    i = (i + 1) % (kvs.length / 100)
    val put = (i * 100 until (i + 1) * 100)
      .map(i => kvs(i))
      .toList
    put.parTraverse { case (k, v) => dbRocks.putRaw(k, v) }.unsafeRunSync()
  }

  @TearDown
  def tearDown(): Unit = {
    close.unsafeRunSync()
    dbMem = KeyValueDB.inmem[IO].unsafeRunSync()
    RocksDB.destroy[IO](dirRocks.path).unsafeToFuture()
  }
}
