package jbok
import scodec.bits.ByteVector

package object persistent {
  type Put = (ColumnFamily, ByteVector, ByteVector)
  object Put {
    def apply(cf: ColumnFamily, key: ByteVector, value: ByteVector): Put = (cf, key, value)
  }

  type Del = (ColumnFamily, ByteVector)
  object Del {
    def apply(cf: ColumnFamily, key: ByteVector): Del = (cf, key)
  }
}
