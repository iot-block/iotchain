---
layout: docsplus
title:  "Admin"
number: 1
---

```scala
trait AdminAPI[F[_]] {
  def peerUri: F[String]

  def addPeer(peerUri: String): F[Unit]

  def dropPeer(peerUri: String): F[Unit]

  def incomingPeers: F[List[PeerUri]]

  def outgoingPeers: F[List[PeerUri]]

  def pendingTransactions: F[List[SignedTransaction]]

  def getConfig: F[FullConfig]
}
```
