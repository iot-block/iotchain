package jbok.core.peer

import jbok.core.messages.Status

case class PeerInfo(remoteStatus: Status, maxBlockNumber: BigInt)
