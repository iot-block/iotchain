---
layout: docsplus
title:  "Contract"
number: 1
---

ApiPrefix: `contract`

```scala mdoc
import jbok.core.api._
import scodec.bits.ByteVector
import jbok.common.math.N

trait ContractAPI[F[_]] {
  def call(callTx: CallTx, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getEstimatedGas(callTx: CallTx, tag: BlockTag = BlockTag.latest): F[N]

  def getGasPrice: F[N]
}
```
