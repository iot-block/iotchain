package jbok.app.txgen

import cats.effect.IO
import javax.net.ssl.SSLContext
import jbok.app.AppSpec
import jbok.app.service.HttpService
import jbok.core.api.JbokClientPlatform
import jbok.core.mining.TxGen

class TxGenClientSpec extends AppSpec {
  "TxGen via client" should {
    "generate txs via client" ignore check { objects =>
      val keyPairs    = testKeyPair :: Nil
      val httpService = objects.get[HttpService[IO]]
      val ssl         = objects.get[Option[SSLContext]]
      httpService.resource.use { server =>
        JbokClientPlatform.resource[IO](server.baseUri.toString(), ssl).use { client =>
          for {
            txGen <- TxGen[IO](keyPairs, client)
            txs   <- txGen.genValidExternalTxN(10)
            _ = println(txs)
          } yield ()
        }
      }
    }
  }
}
