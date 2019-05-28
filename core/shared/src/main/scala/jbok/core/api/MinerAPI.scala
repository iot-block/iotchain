package jbok.core.api

import jbok.core.consensus.poa.clique.Proposal
import jbok.core.models.Address
import jbok.network.rpc.PathName

@PathName("miner")
trait MinerAPI[F[_]] {
  def ballot(address: Address, auth: Boolean): F[Unit]

  def cancelBallot: F[Unit]

  def getBallot: F[Option[Proposal]]

  def isMining: F[Boolean]
}
