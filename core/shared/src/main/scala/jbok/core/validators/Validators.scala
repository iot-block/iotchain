package jbok.core.validators

import cats.effect.Effect
import jbok.core.BlockChain
import jbok.core.configs.{BlockChainConfig, DaoForkConfig}

trait Invalid

trait Validators[F[_]] {
  val blockValidator: BlockValidator[F]
  val blockHeaderValidator: BlockHeaderValidator[F]
  val ommersValidator: OmmersValidator[F]
  val transactionValidator: TransactionValidator[F]
}

object Validators {
  def apply[F[_]: Effect](blockChain: BlockChain[F], blockChainConfig: BlockChainConfig, daoForkConfig: DaoForkConfig) =
    new Validators[F] {
      override val blockValidator: BlockValidator[F] = new BlockValidator[F]()
      override val blockHeaderValidator: BlockHeaderValidator[F] =
        new BlockHeaderValidator[F](blockChain, blockChainConfig, daoForkConfig)
      override val ommersValidator: OmmersValidator[F] = new OmmersValidator[F](blockChain, blockChainConfig, daoForkConfig)
      override val transactionValidator: TransactionValidator[F] = new TransactionValidator[F](blockChainConfig)
    }
}
