package jbok.network.nat

import java.util.UUID

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.network.Request
import jbok.network.client.Client
import jbok.network.server.Server
import jbok.network.testkit._

import scala.concurrent.duration._

class NatSpec extends JbokSpec {
  val externalIP: String = ""
  val internalPort       = 12345
  val externalPort       = 12346
  val server             = random[Server[IO]](genTcpServer(internalPort))
  val client             = random[Client[IO]](genTcpClient(externalPort))

  def checkNat(natType: NatType) =
    s"NAT $natType" should {
      "add and delete mapping" ignore {
        val p = for {
          nat   <- Nat[IO](natType)
          _     <- nat.addMapping(internalPort, externalPort, 120)
          _     <- nat.deleteMapping(externalPort)
          _     <- nat.addMapping(internalPort, externalPort, 120)
          fiber <- server.stream.compile.drain.start
          _     <- T.sleep(1.second)
          _     <- client.start
          _     <- client.write(Request.withTextBody[IO](UUID.randomUUID(), "", "hello"))
          res   <- client.read
          _ = res.bodyAsText.unsafeRunSync() shouldBe "hello"
          _ <- fiber.cancel
        } yield ()

        p.unsafeRunSync()
      }
    }

  checkNat(NatPMP)
  checkNat(NatUPnP)
}
