package jbok.core.genesis

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.ledger.History
import jbok.core.models.Address
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector
import jbok.core.testkit.testGenesis
import jbok.common.testkit._
import jbok.common.execution._

class GenesisConfigSpec extends JbokSpec {
  "GenesisConfig" should {
    "load config alloc" in {
      implicit val chainId = testGenesis.chainId
      val history = History.forBackendAndPath[IO](KeyValueDB.INMEM, "").unsafeRunSync()
      history.initGenesis(testGenesis).unsafeRunSync()
      val addresses = testGenesis.alloc.keysIterator.toList
      val accounts =
        addresses.flatMap(addr => history.getAccount(Address(ByteVector.fromValidHex(addr)), 0).unsafeRunSync())

      accounts.length shouldBe addresses.length
    }
  }
}
