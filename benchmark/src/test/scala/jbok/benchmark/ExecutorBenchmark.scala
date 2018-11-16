package jbok.benchmark

import cats.effect.IO
import jbok.core.ledger.BlockExecutor
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.testkit._
import org.openjdk.jmh.annotations._
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.Level
import org.slf4j.LoggerFactory

class ExecutorBenchmark extends JbokBenchmark {
  val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
  val logger        = loggerContext.getLogger("root")
  logger.setLevel(Level.OFF)

  implicit val fixture = defaultFixture()
  val executor         = random[BlockExecutor[IO]]
  val tx               = random[List[SignedTransaction]](genTxs(1, 1))
  val block            = random[Block](genBlock(stxsOpt = Some(tx)))

  @Benchmark
  def executeBlockTransactions() =
    executor.executeBlockTransactions(block, shortCircuit = true).unsafeRunSync()
}
