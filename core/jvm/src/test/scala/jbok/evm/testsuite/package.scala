package jbok.evm

import io.circe.{Decoder, Encoder, KeyDecoder}
import jbok.codec.json.implicits._
import jbok.core.models.Address
import scodec.bits.ByteVector

package object testsuite {
  implicit val addressDecoder: Decoder[Address] = bytesDecoder.map(s => Address.apply(s))
  implicit val addressEncoder: Encoder[Address] = bytesEncoder.contramap[Address](_.bytes)
  implicit val bytesKeyDecoder =
    KeyDecoder.instance[ByteVector](s => ByteVector.fromHexDescriptive(s).fold(_ => None, Some.apply))
  implicit val addressKeyDecoder: KeyDecoder[Address] = bytesKeyDecoder.map(x => Address(x))
}
