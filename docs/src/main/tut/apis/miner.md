---
layout: docsplus
title:  "Miner"
number: 1
---

ApiPrefix: `miner`

```scala
import jbok.core.models.Address
import jbok.core.consensus.poa.clique.Proposal

trait MinerAPI[F[_]] {
  def ballot(address: Address, auth: Boolean): F[Unit]

  def cancelBallot: F[Unit]

  def getBallot: F[Option[Proposal]]
}
```
