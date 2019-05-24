package jbok.core.api

import jbok.core.consensus.poa.clique.Proposal
import jbok.core.models.Address

trait MinerAPI[F[_]] {
  def ballot(address: Address, auth: Boolean): F[Unit]

  def cancelBallot: F[Unit]

  def getBallot: F[Option[Proposal]]
}
