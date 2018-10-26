package jbok.app

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.core.models.{Address, UInt256}
import jbok.crypto._
import shapeless._

object ContractAddress {
  def getContractAddress(address: Address, nonce: UInt256) = {
    val hash = RlpCodec.encode(address :: nonce :: HNil).require.bytes.kec256
    Address.apply(hash)
  }
}
