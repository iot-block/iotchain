package jbok.core.config

import jbok.common.math.N
import jbok.core.models.{Address, ChainId}

final case class GenesisBuilder(
    base: GenesisConfig = GenesisConfig(),
    alloc: Map[Address, N] = Map.empty,
    miners: List[Address] = Nil,
    chainId: ChainId = ChainId(1)
) {
  def withChainId(id: ChainId): GenesisBuilder =
    copy(chainId = id)

  def addAlloc(address: Address, amount: N): GenesisBuilder =
    copy(alloc = alloc + (address -> amount))

  def addMiner(address: Address): GenesisBuilder =
    copy(miners = miners ++ List(address))

  def build: GenesisConfig =
    base.copy(
      miners = miners,
      alloc = alloc,
      chainId = chainId,
      timestamp = System.currentTimeMillis()
    )
}
