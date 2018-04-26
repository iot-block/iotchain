package jbok.p2p.peer

case class PeerManagerConfig(
    minGoodPeers: Int,
    minConnectedPeers: Int
)

object PeerManagerConfig {
  val defaultConfig = PeerManagerConfig(0, 0)
}
