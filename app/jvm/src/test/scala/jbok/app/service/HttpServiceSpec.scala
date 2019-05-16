package jbok.app.service

import cats.effect.IO
import javax.net.ssl.SSLContext
import jbok.app.{AppModule, AppSpec}
import jbok.core.api.JbokClientPlatform

class HttpServiceSpec extends AppSpec {
  "HttpService" should {
    "server & client with SSL" in {
      val p = AppModule.resource[IO]().use { objects =>
        val httpService = objects.get[HttpService[IO]]
        val ssl         = objects.get[Option[SSLContext]]
        httpService.resource.use { server =>
          JbokClientPlatform.resource[IO](server.baseUri.toString(), ssl).use { client =>
            for {
              headers <- client.block.getBlockHeadersByNumber(0, 100)
              _ = headers.length shouldBe 1
            } yield ()
          }
        }
      }
      p.unsafeRunSync()
    }
  }
}
