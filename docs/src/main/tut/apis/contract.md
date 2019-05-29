---
layout: docsplus
title:  "Contract"
number: 1
---

```scala
trait ContractAPI[F[_]] {
  def call(callTx: CallTx, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getEstimatedGas(callTx: CallTx, tag: BlockTag = BlockTag.latest): F[BigInt]

  def getGasPrice: F[BigInt]
}
```
