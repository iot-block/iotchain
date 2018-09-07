package jbok.core.genesis

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.History
import jbok.core.models.Address
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

class GenesisConfigSpec extends JbokSpec {
  "GenesisConfig" should {
    "load config" in {
      val db = KeyValueDB.inMemory[IO].unsafeRunSync()
      val blockChain = History[IO](db).unsafeRunSync()
      blockChain.loadGenesisConfig(GenesisConfig.default).unsafeRunSync()
      val addresses = GenesisConfig.default.alloc.keysIterator.toList
      val account = blockChain.getAccount(Address(ByteVector.fromValidHex(addresses.head)), 0).unsafeRunSync()
    }
  }
}
