---
layout: docsplus
title:  "Block"
number: 1
---

```scala mdoc
import jbok.core.models._
import scodec.bits.ByteVector 
import jbok.common.math.N

trait BlockAPI[F[_]] {
  def getBestBlockNumber: F[N]

  def getBlockHeaderByNumber(number: N): F[Option[BlockHeader]]

  def getBlockHeadersByNumber(start: N, limit: Int): F[List[BlockHeader]]

  def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]]

  def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]]

  def getBlockBodies(hashes: List[ByteVector]): F[List[BlockBody]]

  def getBlockByNumber(number: N): F[Option[Block]]

  def getBlockByHash(hash: ByteVector): F[Option[Block]]

  def getTransactionCountByHash(hash: ByteVector): F[Option[Int]]

  def getTotalDifficultyByNumber(number: N): F[Option[N]]

  def getTotalDifficultyByHash(hash: ByteVector): F[Option[N]]
}
```
