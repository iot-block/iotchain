---
layout: docsplus
title:  "Block"
number: 1
---

```scala
trait BlockAPI[F[_]] {
  def getBestBlockNumber: F[BigInt]

  def getBlockHeaderByNumber(number: BigInt): F[Option[BlockHeader]]

  def getBlockHeadersByNumber(start: BigInt, limit: Int): F[List[BlockHeader]]

  def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]]

  def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]]

  def getBlockBodies(hashes: List[ByteVector]): F[List[BlockBody]]

  def getBlockByNumber(number: BigInt): F[Option[Block]]

  def getBlockByHash(hash: ByteVector): F[Option[Block]]

  def getTransactionCountByHash(hash: ByteVector): F[Option[Int]]

  def getTotalDifficultyByNumber(number: BigInt): F[Option[BigInt]]

  def getTotalDifficultyByHash(hash: ByteVector): F[Option[BigInt]]
}
```
