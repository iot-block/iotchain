package jbok.app.service.store.doobie

import doobie.util.Meta
import jbok.core.models.Address
import scodec.bits.ByteVector

trait DoobieSupport {
  implicit val metaByteVector: Meta[ByteVector] = Meta.StringMeta.imap[ByteVector](ByteVector.fromValidHex(_))(_.toHex)

  implicit val metaBigInt: Meta[BigInt] = Meta.StringMeta.imap[BigInt](BigInt.apply)(_.toString(10))

  implicit val metaAddress: Meta[Address] = Meta.StringMeta.imap[Address](Address.fromHex)(_.toString)
}
