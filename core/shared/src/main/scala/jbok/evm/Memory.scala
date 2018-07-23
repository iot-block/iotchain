package jbok.evm

import jbok.core.models.UInt256
import scodec.bits.ByteVector

object Memory {
  def empty: Memory = Memory(ByteVector.empty)

  private def zeros(size: Int): ByteVector = ByteVector(Array.fill[Byte](size)(0))
}

case class Memory(underlying: ByteVector) extends AnyVal {

  import Memory.zeros

  def store(offset: UInt256, b: Byte): Memory = store(offset, ByteVector(b))

  def store(offset: UInt256, uint: UInt256): Memory = store(offset, uint.bytes)

  def store(offset: UInt256, bytes: Array[Byte]): Memory = store(offset, ByteVector(bytes))

  /** Stores data at the given offset.
    * The memory is automatically expanded to accommodate new data - filling empty regions with zeroes if necessary -
    * hence an OOM error may be thrown.
    */
  def store(offset: UInt256, data: ByteVector): Memory = {
    val idx: Long = offset.toLong

    val newUnderlying: ByteVector =
      if (data.isEmpty)
        underlying
      else
        underlying.take(idx).padTo(idx) ++ data ++ underlying.drop(idx + data.length)

    Memory(newUnderlying)
  }

  def load(offset: UInt256): (UInt256, Memory) =
    doLoad(offset, UInt256.Size) match {
      case (bs, memory) => (UInt256(bs), memory)
    }

  def load(offset: UInt256, size: UInt256): (ByteVector, Memory) = doLoad(offset, size.toInt)

  /** Returns a ByteVector of a given size starting at the given offset of the Memory.
    * The memory is automatically expanded (with zeroes) when reading previously uninitialised regions,
    * hence an OOM error may be thrown.
    */
  private def doLoad(offset: UInt256, size: Int): (ByteVector, Memory) =
    if (size <= 0) {
      (ByteVector.empty, this)
    } else {
      val start: Int = offset.toInt
      val end: Int = start + size

      val newUnderlying =
        if (end <= underlying.size)
          underlying
        else
          underlying ++ zeros(end - underlying.size.toInt)

      (newUnderlying.slice(start, end), Memory(newUnderlying))
    }

  /** This function will expand the Memory size as if storing data given the `offset` and `size`.
    * If the memory is already initialised at that region it will not be modified, otherwise it will be filled with
    * zeroes.
    * This is required to satisfy memory expansion semantics for *CALL* opcodes.
    */
  def expand(offset: UInt256, size: UInt256): Memory = {
    val totalSize = (offset + size).toInt
    if (this.size >= totalSize || size.isZero) {
      this
    } else {
      val fill = zeros(totalSize - this.size)
      Memory(underlying ++ fill)
    }
  }

  /**
    * @return memory size in bytes
    */
  def size: Int = underlying.length.toInt
}
