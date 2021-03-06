package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.core.api.MinerAPI
import jbok.core.consensus.poa.clique.{CliqueExtra, Proposal}
import jbok.core.mining.BlockMiner
import jbok.core.models.Address

class MinerAPISpec extends AppSpec {
  "MinerAPI" should {
    "ballot" in check { objects =>
      val minerAPI = objects.get[MinerAPI[IO]]
      val miner    = objects.get[BlockMiner[IO]]

      val address = Address(100)

      for {
        _ <- minerAPI.ballot(address, true)
        expectedProposal = Some(Proposal(address, true))
        proposal <- minerAPI.getBallot
        _ = proposal shouldBe expectedProposal
        minedBlock <- miner.mine()

        extra <- IO.fromEither(minedBlock.block.header.extraAs[CliqueExtra])
        _ = extra.proposal shouldBe expectedProposal
      } yield ()
    }

    "cancelBallot" in check { objects =>
      val minerAPI = objects.get[MinerAPI[IO]]
      val miner    = objects.get[BlockMiner[IO]]

      val address = Address(100)
      for {
        _ <- minerAPI.ballot(address, true)
        expectedProposal = Some(Proposal(address, true))
        proposal <- minerAPI.getBallot
        _ = proposal shouldBe expectedProposal
        _         <- minerAPI.cancelBallot
        proposal2 <- minerAPI.getBallot
        _ = proposal2 shouldBe None
        minedBlock <- miner.mine()
        extra      <- IO.fromEither(minedBlock.block.header.extraAs[CliqueExtra])
        _ = extra.proposal shouldBe None
      } yield ()
    }
  }

}
