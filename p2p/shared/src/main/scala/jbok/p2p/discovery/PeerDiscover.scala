package jbok.p2p.discovery

import fs2.async.Ref
import scodec.bits.ByteVector

case class DiscoveryNodeInfo(node: PeerNode, addTimestamp: Long)

class PeerDiscover[F[_]](discovered: Ref[F, Map[ByteVector, DiscoveryNodeInfo]], config: DiscoveryConfig)
