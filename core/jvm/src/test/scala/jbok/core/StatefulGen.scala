package jbok.core

import cats.effect.IO
import jbok.core.ledger.History
import jbok.core.ledger.TypedBlock.MinedBlock
import jbok.core.mining.{BlockMiner, TxGen}
import jbok.core.models.{Block, SignedTransaction}
import org.scalacheck.Gen

object StatefulGen {
  import CoreSpec._

  def transactions(min: Int = 0, max: Int = 32, history: History[IO] = locator.unsafeRunSync().get[History[IO]]): Gen[List[SignedTransaction]] =
    for {
      size <- Gen.chooseNum(min, max)
      txGen = TxGen[IO](testKeyPair :: Nil, history).unsafeRunSync()
      txs   = txGen.genValidExternalTxN(size).unsafeRunSync()
    } yield txs

  def blocks(min: Int = 1, max: Int = 1): Gen[List[Block]] = {
    val objects = locator.unsafeRunSync()
    val miner   = objects.get[BlockMiner[IO]]
    for {
      size <- Gen.chooseNum(min, max)
      blocks = miner.mineN(size).unsafeRunSync()
    } yield blocks.map(_.block)
  }

  def block(parent: Option[Block] = None, stxs: Option[List[SignedTransaction]] = None): Gen[Block] =
    minedBlock(parent, stxs).map(_.block)

  def minedBlock(parent: Option[Block] = None, stxs: Option[List[SignedTransaction]] = None): Gen[MinedBlock] = {
    val objects = locator.unsafeRunSync()
    val miner   = objects.get[BlockMiner[IO]]
    miner.mine(parent, stxs).unsafeRunSync()
  }
}
