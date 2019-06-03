package jbok.app.service

import cats.effect.IO
import javax.net.ssl.SSLContext
import jbok.app.AppSpec
import jbok.core.api.{BlockTag, JbokClientPlatform}
import jbok.core.config.FullConfig

class HttpServiceSpec extends AppSpec {
  "HttpService" should {
    "server & client with or without SSL" in check { objects =>
      val config      = objects.get[FullConfig]
      val httpService = objects.get[HttpService[IO]]
      val ssl         = objects.get[Option[SSLContext]]
      httpService.resource.use { server =>
        JbokClientPlatform.resource[IO](server.baseUri.toString(), ssl).use { client =>
          for {
            resp <- client.account.getAccount(config.mining.address, BlockTag("2"))
            _ = println(resp)
          } yield ()
        }
      }
    }
  }
}
