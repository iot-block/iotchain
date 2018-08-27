package jbok.examples.hg

import jbok.core.models.SignedTransaction
import scodec.bits.ByteVector

/**
  * @param selfParent hash of the previous self created event
  * @param otherParent hash of the received sync event
  * @param creator creator's proposition
  * @param timestamp creator's creating timestamp
  * @param stxs optional payload
  *
  */
case class EventBody(
    selfParent: ByteVector,
    otherParent: ByteVector,
    creator: ByteVector,
    timestamp: Long,
    index: Int,
    stxs: List[SignedTransaction]
)

