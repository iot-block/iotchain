package jbok.p2p.peer

import jbok.p2p.address.MultiAddr

case class PeerInfo(id: PeerId, knownAddrs: Seq[MultiAddr])
