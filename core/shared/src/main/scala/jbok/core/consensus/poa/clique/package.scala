package jbok.core.consensus.poa

import jbok.core.config.GenesisConfig
import jbok.core.models.Address

package object clique {
  def generateGenesisConfig(template: GenesisConfig, signers: List[Address]): GenesisConfig =
    template.copy(
      extraData = Clique.fillExtraData(signers),
      timestamp = System.currentTimeMillis()
    )
}
