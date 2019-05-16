package jbok.core.config

import jbok.core.models.Address

final case class GenesisBuilder(
    base: GenesisConfig = GenesisConfig(),
    alloc: Map[Address, BigInt] = Map.empty,
    miners: List[Address] = Nil,
    chainId: BigInt = BigInt(1)
) {
  def withChainId(id: BigInt): GenesisBuilder =
    copy(chainId = id)

  def addAlloc(address: Address, amount: BigInt): GenesisBuilder =
    copy(alloc = alloc + (address -> amount))

  def addMiner(address: Address): GenesisBuilder =
    copy(miners = miners ++ List(address))

  def build: GenesisConfig =
    base.copy(
      miners = miners,
      alloc = alloc,
      chainId = this.chainId,
      timestamp = System.currentTimeMillis()
    )
}
