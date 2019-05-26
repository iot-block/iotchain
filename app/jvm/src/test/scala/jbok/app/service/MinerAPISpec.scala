package jbok.app.service

import cats.effect.IO
import cats.implicits._
import jbok.app.AppSpec
import jbok.core.api.MinerAPI
import jbok.core.consensus.poa.clique.{CliqueExtra, Proposal}
import jbok.core.mining.BlockMiner
import jbok.core.models.Address
import jbok.codec.rlp.implicits._

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

        extra <- minedBlock.block.header.extraAs[IO, CliqueExtra]
        _ = extra.proposal shouldBe expectedProposal
        _         <- miner.mine(minedBlock.block.some)
        proposal2 <- minerAPI.getBallot
        _ = proposal2 shouldBe None
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
        extra      <- minedBlock.block.header.extraAs[IO, CliqueExtra]
        _ = extra.proposal shouldBe None
      } yield ()
    }
  }

}
