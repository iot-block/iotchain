package jbok.core.models

import scodec.bits.ByteVector

final case class TransactionLocation(blockHash: ByteVector, txIndex: Int)
