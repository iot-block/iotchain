package jbok.benchmark

import java.nio.charset.StandardCharsets

import cats.effect.IO
import cats.implicits._
import jbok.persistent.leveldb.{LevelDB, LevelDBConfig}
import monix.eval.Task
import monix.execution.Scheduler
import org.openjdk.jmh.annotations._
import org.scalacheck.Gen

import scala.concurrent.duration.Duration

class PersistentBenchmark extends JbokBenchmark {
  @Param(Array("100000"))
  var size: Int = _

  var kvs: Vector[(String, String)] = _

  implicit val codec = scodec.codecs.string(StandardCharsets.UTF_8)


  var i = 0

  @Benchmark
  def vanilla() = {
    db.db.put(codec.encode(kvs(i)._1).require.toByteArray, codec.encode(kvs(i)._2).require.toByteArray)
    i = (i + 1) % size
  }

  val db = LevelDB[IO](LevelDBConfig("test")).unsafeRunSync()
  @Benchmark
  def ioPut() = {
    db.put[String, String](kvs(i)._1, kvs(i)._2).unsafeRunSync()
    i = (i + 1) % size
  }


  implicit val scheduler = Scheduler.computation(8)
  val monixDB = LevelDB[Task](LevelDBConfig("test")).runSyncUnsafe(Duration.Inf)
  @Benchmark
  def monixPut() = {
    monixDB.put[String, String](kvs(i)._1, kvs(i)._2).runAsync
    i = (i + 1) % size
  }

  @Benchmark
  def batchPut() = {
    val n = 100
    val batches = kvs.map { case (k, v) => db.putOp[String, String](k, v) }.grouped(n)
    batches.foreach(xs => db.writeBatch(xs).unsafeRunSync())
  }

  @TearDown
  def teardown() = {
    db.close.unsafeRunSync()
    db.destroy.unsafeRunSync()
  }

  @Setup
  def setup() = {
    kvs = (for {
      key <- Gen.alphaStr
      value <- Gen.alphaStr
      kvs <- Gen.listOfN(size, (key, value))
    } yield kvs).sample.get.toVector
  }
}
