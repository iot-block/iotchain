package jbok.app.service

import cats.effect.IO
import cats.implicits._
import jbok.app.AppSpec
import jbok.core.api.MinerAPI
import jbok.core.consensus.poa.clique.{Clique, CliqueConsensus, CliqueExtra, Proposal}
import jbok.core.mining.BlockMiner
import jbok.core.models.Address

class MinerAPISpec extends AppSpec {
  "MinerAPI" should {
    "ballot" in {
      val objects  = locator.unsafeRunSync()
      val minerAPI = objects.get[MinerAPI[IO]]
      val miner    = objects.get[BlockMiner[IO]]

      val address = Address(100)
      minerAPI.ballot(address, true)
      val expectedProposal = Some(Proposal(address, true))
      minerAPI.getBallot.unsafeRunSync() shouldBe expectedProposal

      val minedBlock = miner.stream.take(1).compile.toList.unsafeRunSync().head
      minedBlock.block.header.extraAs[IO, CliqueExtra].map(_.proposal).unsafeRunSync() shouldBe expectedProposal

      miner.mine1(minedBlock.block.some).unsafeRunSync()
      minerAPI.getBallot.unsafeRunSync() shouldBe None
    }

    "cancelBallot" in {
      val objects  = locator.unsafeRunSync()
      val minerAPI = objects.get[MinerAPI[IO]]
      val miner    = objects.get[BlockMiner[IO]]

      val address = Address(100)
      minerAPI.ballot(address, true)
      val expectedProposal = Some(Proposal(address, true))
      minerAPI.getBallot.unsafeRunSync() shouldBe expectedProposal

      minerAPI.cancelBallot.unsafeRunSync()
      minerAPI.getBallot.unsafeRunSync() shouldBe None

      val minedBlock = miner.stream.take(1).compile.toList.unsafeRunSync().head
      minedBlock.block.header.extraAs[IO, CliqueExtra].map(_.proposal).unsafeRunSync() shouldBe None
    }
  }

}
