---
layout: docsplus
title:  "Miner"
number: 1
---

```scala
trait MinerAPI[F[_]] {
  def ballot(address: Address, auth: Boolean): F[Unit]

  def cancelBallot: F[Unit]

  def getBallot: F[Option[Proposal]]
}
```
