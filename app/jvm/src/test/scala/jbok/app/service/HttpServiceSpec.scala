package jbok.app.service

import cats.effect.IO
import javax.net.ssl.SSLContext
import jbok.app.AppSpec
import jbok.core.api.JbokClientPlatform
import jbok.core.mining.BlockMiner
import jbok.core.models.Address

class HttpServiceSpec extends AppSpec {
  "HttpService" should {
    "server & client with or without SSL" in check { objects =>
      val httpService = objects.get[HttpService[IO]]
      val miner       = objects.get[BlockMiner[IO]]
      val ssl         = objects.get[Option[SSLContext]]
      httpService.resource.use { server =>
        JbokClientPlatform.resource[IO](server.baseUri.toString(), ssl).use { client =>
          for {
            headers <- client.block.getBlockHeadersByNumber(0, 100)
            _ = headers.length shouldBe 1

            raddress = Address(100)
            hash <- client.personal.sendTransaction(testAllocAddress, "changeit", Some(raddress), Some(10000), Some(21000))
            _    <- miner.mine()
            tx   <- client.transaction.getTx(hash)
            _ = tx.flatMap(_.senderAddress).contains(testAllocAddress) shouldBe true
          } yield ()
        }
      }
    }
  }
}
