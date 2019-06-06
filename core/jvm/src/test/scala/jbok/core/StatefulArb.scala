package jbok.core

import cats.effect.IO
import jbok.core.ledger.History
import jbok.core.models.{Block, SignedTransaction}
import org.scalacheck.Arbitrary

trait StatefulArb {
  implicit def arbTransactions(implicit history: History[IO]): Arbitrary[List[SignedTransaction]] =
    Arbitrary(StatefulGen.transactions(min = 1, max = 100, history = history))

  implicit def arbBlocks: Arbitrary[List[Block]] =
    Arbitrary(StatefulGen.blocks(1, 10))
}

object StatefulArb extends StatefulArb
