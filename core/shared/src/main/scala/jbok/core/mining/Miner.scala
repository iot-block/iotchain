package jbok.core.mining

import cats.effect.Sync
import fs2.async.Ref
import jbok.core.configs.MiningConfig
import jbok.core.{BlockChain, TxPool}

class Miner[F[_]: Sync](
    blockChain: BlockChain[F],
    txPool: TxPool[F],
    miningConfig: MiningConfig,

    currentEpoch: Ref[F, Long],
    currentEpochDagSize: Ref[F, Long],
    currentEpochDag: Ref[F, Array[Array[Int]]]
) {
  
}
