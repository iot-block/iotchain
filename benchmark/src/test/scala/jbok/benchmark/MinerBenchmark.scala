package jbok.benchmark

import cats.effect.IO
import jbok.core.mining.BlockMiner
import jbok.common.testkit._
import jbok.core.testkit._
import org.openjdk.jmh.annotations._

class MinerBenchmark extends JbokBenchmark {
  implicit val fixture = defaultFixture()
  val miner            = random[BlockMiner[IO]]

  @Benchmark
  def mineWithoutTransactions() =
    miner.mineAndSubmit().unsafeRunSync()
}
