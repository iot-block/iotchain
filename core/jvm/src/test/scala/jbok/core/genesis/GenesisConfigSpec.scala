package jbok.core.genesis

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.History
import jbok.core.config.GenesisConfig
import jbok.core.models.Address
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

class GenesisConfigSpec extends JbokSpec {
  "GenesisConfig" should {
    "load config alloc" in {
      val db      = KeyValueDB.inmem[IO].unsafeRunSync()
      val history = History[IO](db).unsafeRunSync()
      history.init(GenesisConfig.default).unsafeRunSync()
      val addresses = GenesisConfig.default.alloc.keysIterator.toList
      val accounts =
        addresses.flatMap(addr => history.getAccount(Address(ByteVector.fromValidHex(addr)), 0).unsafeRunSync())

      accounts.length shouldBe addresses.length
    }
  }
}
