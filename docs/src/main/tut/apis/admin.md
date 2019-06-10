---
layout: docsplus
title:  "Admin"
number: 1
---

```scala mdoc
import jbok.core.models._
import jbok.core.peer.PeerUri
import jbok.core.config.FullConfig

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
