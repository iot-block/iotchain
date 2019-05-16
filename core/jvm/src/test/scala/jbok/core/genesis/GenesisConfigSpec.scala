package jbok.core.genesis

import cats.effect.IO
import jbok.core.CoreSpec
import jbok.core.ledger.History
import jbok.core.models.Address
import scodec.bits.ByteVector

class GenesisConfigSpec extends CoreSpec {
  "GenesisConfig" should {
    "load config alloc" in {
      val objects   = locator.unsafeRunSync()
      val history   = objects.get[History[IO]]
      val addresses = genesis.alloc.keysIterator.toList
      val accounts  = addresses.flatMap(addr => history.getAccount(Address(ByteVector.fromValidHex(addr)), 0).unsafeRunSync())

      accounts.length shouldBe addresses.length
    }
  }
}
