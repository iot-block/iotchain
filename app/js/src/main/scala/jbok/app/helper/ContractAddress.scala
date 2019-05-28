package jbok.app.helper

import jbok.codec.rlp.implicits._
import jbok.core.models.{Address, UInt256}
import jbok.crypto._

object ContractAddress {
  def getContractAddress(address: Address, nonce: UInt256): Address = {
    val hash = (address, UInt256(nonce)).asBytes.kec256
    Address.apply(hash)
  }
}
