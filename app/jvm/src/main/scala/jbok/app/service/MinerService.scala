package jbok.app.service

import cats.effect.Sync
import jbok.core.api.MinerAPI
import jbok.core.consensus.poa.clique.{Clique, Proposal}
import jbok.core.models.Address

final class MinerService[F[_]](clique: Clique[F])(implicit F: Sync[F]) extends MinerAPI[F] {
  override def ballot(address: Address, auth: Boolean): F[Unit] = clique.ballot(address, auth)

  override def cancelBallot: F[Unit] = clique.cancelBallot

  override def getBallot: F[Option[Proposal]] = clique.getProposal
}
