package jbok.app.service.store.doobie

import doobie.util.Meta
import jbok.common.math.N
import jbok.core.models.Address
import scodec.bits.ByteVector

trait DoobieSupport {
  implicit val metaByteVector: Meta[ByteVector] = Meta.StringMeta.imap[ByteVector](ByteVector.fromValidHex(_))(_.toHex)

  implicit val metaN: Meta[N] = Meta.StringMeta.imap[N](N.apply)(_.toString)

  implicit val metaAddress: Meta[Address] = Meta.StringMeta.imap[Address](Address.fromHex)(_.toString)
}
