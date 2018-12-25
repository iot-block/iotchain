package jbok.app

import jbok.codec.rlp.implicits._
import jbok.core.models.{Address, UInt256}

object ContractAddress {
  def getContractAddress(address: Address, nonce: UInt256) = {
    val hash = (address, nonce).asBytes
    Address.apply(hash)
  }
}
