package jbok

package object persistent {
  type Put = (ColumnFamily, Array[Byte], Array[Byte])
  object Put {
    def apply(cf: ColumnFamily, key: Array[Byte], value: Array[Byte]): Put = (cf, key, value)
  }

  type Del = (ColumnFamily, Array[Byte])
  object Del {
    def apply(cf: ColumnFamily, key: Array[Byte]): Del = (cf, key)
  }
}
