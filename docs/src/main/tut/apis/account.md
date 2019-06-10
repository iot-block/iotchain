---
layout: docsplus
title:  "Account"
number: 1
---

```scala mdoc
import scodec.bits.ByteVector
import jbok.common.math.N
import jbok.core.models._
import jbok.core.api._

trait AccountAPI[F[_]] {
  def getAccount(address: Address, tag: BlockTag = BlockTag.latest): F[Account]

  def getCode(address: Address, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getBalance(address: Address, tag: BlockTag = BlockTag.latest): F[N]

  def getStorageAt(address: Address, position: N, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getTransactions(address: Address): F[List[HistoryTransaction]]

  def getPendingTxs(address: Address): F[List[SignedTransaction]]

  def getEstimatedNonce(address: Address): F[N]
}
```
