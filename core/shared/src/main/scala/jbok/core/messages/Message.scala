package jbok.core.messages

import scodec.bits.ByteVector

trait Message {
  def asBytes: ByteVector = ???
}
