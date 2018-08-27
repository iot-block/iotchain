package jbok.core.consensus

import cats.effect.Sync
import jbok.core.BlockChain
import jbok.core.models._
import scodec.bits.ByteVector
import cats.implicits._

trait ChainReader[F[_]] {
  // Config retrieves the blockchain's chain configuration.
  //  Config() *params.ChainConfig

  // CurrentHeader retrieves the current header from the local chain.
  def currentHeader: F[BlockHeader]

  // GetHeader retrieves a block header from the database by hash and number.
  def getHeader(hash: ByteVector, number: BigInt): F[BlockHeader]

  // GetHeaderByNumber retrieves a block header from the database by number.
  def getHeaderByNumber(number: BigInt): F[BlockHeader]

  // GetHeaderByHash retrieves a block header from the database by its hash.
  def getHeaderByHash(hash: ByteVector): F[BlockHeader]

  // GetBlock retrieves a block from the database by hash and number.
  def getBlock(hash: ByteVector, number: BigInt): F[Block]
}

object ChainReader {
  def apply[F[_]: Sync](blockChain: BlockChain[F]): ChainReader[F] = new ChainReader[F] {
    override def currentHeader: F[BlockHeader] =
      blockChain.getBestBlock.map(_.header)

    override def getHeader(hash: ByteVector, number: BigInt): F[BlockHeader] =
      blockChain.getBlockHeaderByHash(hash).map(_.get)

    override def getHeaderByNumber(number: BigInt): F[BlockHeader] =
      blockChain.getBlockHeaderByNumber(number).map(_.get)

    override def getHeaderByHash(hash: ByteVector): F[BlockHeader] =
      blockChain.getBlockHeaderByHash(hash).map(_.get)

    override def getBlock(hash: ByteVector, number: BigInt): F[Block] =
      blockChain.getBlockByHash(hash).map(_.get)
  }
}
