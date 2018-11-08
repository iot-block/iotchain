package jbok.benchmark
import better.files._
import cats.effect.IO
import jbok.common.execution._
import jbok.common.testkit._
import jbok.persistent.KeyValueDB
import jbok.persistent.leveldb.LevelDB
import jbok.core.testkit._
import org.openjdk.jmh.annotations.{Benchmark, OperationsPerInvocation, TearDown}
import org.scalacheck.Gen

class KeyValueDBBenchmark extends JbokBenchmark {
  val size = 10000

  val blockHeaders = Gen.listOfN(size, arbBlockHeader.arbitrary).sample.get.toArray

  var i = 0

  val keyGen   = genBoundedByteVector(16, 16)
  val valueGen = genBoundedByteVector(100, 100)

  val keys   = Gen.listOfN(size, keyGen).sample.get
  val values = Gen.listOfN(size, valueGen).sample.get
  val kvs    = keys.zip(values).toArray

  val dirIq80 = File.newTemporaryDirectory()
  val dirJni  = File.newTemporaryDirectory()

  val dbIq80 = LevelDB[IO](dirIq80.pathAsString, useJni = false).unsafeRunSync()
  val dbJni  = LevelDB[IO](dirJni.pathAsString, useJni = true).unsafeRunSync()
  var dbMem  = KeyValueDB.inmem[IO].unsafeRunSync()

  @Benchmark
  def putIq80() = {
    val (k, v) = kvs(i)
    i = (i + 1) % kvs.length
    dbIq80.putRaw(k, v).unsafeRunSync()
  }

  @Benchmark
  def putJni() = {
    val (k, v) = kvs(i)
    i = (i + 1) % kvs.length
    dbJni.putRaw(k, v).unsafeRunSync()
  }

  @Benchmark
  def putMem() = {
    val (k, v) = kvs(i)
    i = (i + 1) % kvs.length
    dbMem.putRaw(k, v).unsafeRunSync()
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def putBatchIq80() = {
    i = (i + 1) % (kvs.length / 100)
    val put = (i * 100 until (i + 1) * 100)
      .map(i => kvs(i))
      .toList
    dbIq80.writeBatchRaw(put, Nil).unsafeRunSync()
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def putBatchJni() = {
    i = (i + 1) % (kvs.length / 100)
    val put = (i * 100 until (i + 1) * 100)
      .map(i => kvs(i))
      .toList
    dbJni.writeBatchRaw(put, Nil).unsafeRunSync()
  }

  @Benchmark
  @OperationsPerInvocation(100)
  def putBatchMem() = {
    i = (i + 1) % (kvs.length / 100)
    val put = (i * 100 until (i + 1) * 100)
      .map(i => kvs(i))
      .toList
    dbMem.writeBatchRaw(put, Nil).unsafeRunSync()
  }

  @TearDown
  def tearDown(): Unit = {
    dbIq80.close.unsafeRunSync()
    dbJni.close.unsafeRunSync()
    dbMem = KeyValueDB.inmem[IO].unsafeRunSync()
    LevelDB.destroy[IO](dirIq80.pathAsString, useJni = false).unsafeRunSync()
    LevelDB.destroy[IO](dirJni.pathAsString, useJni = true).unsafeRunSync()
  }
}
