package jbok.core

import cats.effect.IO
import jbok.core.configs.{BlockChainConfig, DaoForkConfig}

trait BlockChainFixture {
  val blockChainConfig = BlockChainConfig()

  val daoForkConfig = DaoForkConfig()

  val blockChain = newBlockChain

  val blockChain2 = newBlockChain

  val blockChain3 = newBlockChain

  def newBlockChain = BlockChain.inMemory[IO].unsafeRunSync()
}
