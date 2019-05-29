---
layout: docsplus
title:  "Account"
number: 1
---

```scala
trait AccountAPI[F[_]] {
  def getAccount(address: Address, tag: BlockTag = BlockTag.latest): F[Account]

  def getCode(address: Address, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getBalance(address: Address, tag: BlockTag = BlockTag.latest): F[BigInt]

  def getStorageAt(address: Address, position: BigInt, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getTransactions(address: Address): F[List[HistoryTransaction]]

  def getPendingTxs(address: Address): F[List[SignedTransaction]]

  def getEstimatedNonce(address: Address): F[BigInt]
}
```
