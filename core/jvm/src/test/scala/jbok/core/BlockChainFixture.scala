package jbok.core

import cats.effect.IO
import jbok.core.configs.{BlockChainConfig, DaoForkConfig}

trait BlockChainFixture {
  val blockChainConfig = BlockChainConfig()

  val daoForkConfig = DaoForkConfig()

  lazy val blockChain = newBlockChain

  lazy val blockChain2 = newBlockChain

  lazy val blockChain3 = newBlockChain

  def newBlockChain = BlockChain.inMemory[IO]().unsafeRunSync()
}
