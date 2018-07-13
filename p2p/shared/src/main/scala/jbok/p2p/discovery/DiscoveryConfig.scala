package jbok.p2p.discovery

import scala.concurrent.duration.FiniteDuration

case class DiscoveryConfig(
    bind: String,
    port: Int,
    seeds: Set[PeerNode],
    discoverInterval: FiniteDuration
)
