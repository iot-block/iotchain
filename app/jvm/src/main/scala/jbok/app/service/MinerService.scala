package jbok.app.service

import cats.effect.Concurrent
import jbok.core.api.MinerAPI
import jbok.core.config.MiningConfig
import jbok.core.consensus.poa.clique.{Clique, Proposal}
import jbok.core.models.Address

final class MinerService[F[_]](clique: Clique[F], miningConfig: MiningConfig)(implicit F: Concurrent[F]) extends MinerAPI[F] {
  override def ballot(address: Address, auth: Boolean): F[Unit] = clique.ballot(address, auth)

  override def cancelBallot: F[Unit] = clique.cancelBallot

  override def getBallot: F[Option[Proposal]] = clique.getProposal

  override def isMining: F[Boolean] = F.pure(miningConfig.enabled)
}
