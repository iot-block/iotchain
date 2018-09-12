package jbok.core.ledger

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit.ByteGen
import jbok.core.History
import jbok.core.models.{Account, Address, UInt256}
import jbok.core.store.{AddressAccountStore, EvmCodeStore}
import jbok.evm.WorldStateProxy
import jbok.persistent.{KeyValueDB, SnapshotKeyValueStore}
import jbok.testkit.VMGens
import scodec.bits._

class WorldStateProxySpec extends JbokSpec {
  trait Fixture {
    val db           = KeyValueDB.inMemory[IO].unsafeRunSync()
    val history   = History[IO](db).unsafeRunSync()
    val accountStore = AddressAccountStore(db).unsafeRunSync()
    val accountProxy = SnapshotKeyValueStore(accountStore)

    val world = history
      .getWorldStateProxy(-1, UInt256.Zero, None, noEmptyAccounts = false)
      .unsafeRunSync()

    val postEIP161WorldState = history
      .getWorldStateProxy(-1, UInt256.Zero, None, noEmptyAccounts = true)
      .unsafeRunSync()

    val address1 = Address(0x123456)
    val address2 = Address(0xabcdef)
    val address3 = Address(0xfedcba)
  }

  "WorldStateProxy" should {
    "create then get account" in new Fixture {
      world.newEmptyAccount(address1).accountExists(address1).unsafeRunSync() shouldBe true
    }

    "put then get code" in new Fixture {
      forAll(ByteGen.genBoundedByteVector(0, 100)) { code =>
        world.putCode(address1, code).getCode(address1).unsafeRunSync() shouldBe code
      }
    }

    "put then get storage" in new Fixture {
      val addr  = VMGens.getUInt256Gen().sample.getOrElse(UInt256.MaxValue)
      val value = VMGens.getUInt256Gen().sample.getOrElse(UInt256.MaxValue)

      val storage = world
        .getStorage(address1)
        .unsafeRunSync()
        .store(addr, value)

      world
        .putStorage(address1, storage)
        .getStorage(address1)
        .unsafeRunSync()
        .load(addr)
        .unsafeRunSync() shouldEqual value
    }

    "transfer value to other address" in new Fixture {
      val account    = Account(0, 100)
      val toTransfer = account.balance - 20
      val finalWorldState = world
        .putAccount(address1, account)
        .newEmptyAccount(address2)
        .transfer(address1, address2, UInt256(toTransfer))
        .unsafeRunSync()

      finalWorldState.getAccount(address1).unsafeRunSync().balance shouldBe (account.balance - toTransfer)
      finalWorldState.getAccount(address2).unsafeRunSync().balance shouldBe toTransfer
    }

    "not store within contract store if value is zero" in new Fixture {
      val account = Account(0, 100)
      val world1  = world.putAccount(address1, account).persisted.unsafeRunSync()

      val world2 =
        world1
          .putStorage(address1,
                      world
                        .getStorage(address1)
                        .unsafeRunSync()
                        .store(UInt256.One, UInt256.Zero))
          .persisted
          .unsafeRunSync()

      world1.stateRootHash shouldBe world2.stateRootHash
    }

    "storing a zero on a contract store position should remove it from the underlying tree" in new Fixture {
      val account = Account(0, 100)
      val world1  = world.putAccount(address1, account).persisted.unsafeRunSync()

      val world2 =
        world1
          .putStorage(
            address1,
            world
              .getStorage(address1)
              .unsafeRunSync()
              .store(UInt256.One, UInt256.One)
          )
          .persisted
          .unsafeRunSync()

      (world1.stateRootHash == world2.stateRootHash) shouldBe false

      val world3 =
        world1
          .putStorage(address1,
                      world
                        .getStorage(address1)
                        .unsafeRunSync()
                        .store(UInt256.One, UInt256.Zero))
          .persisted
          .unsafeRunSync()

      world1.stateRootHash shouldBe world3.stateRootHash
    }

    "be able to persist changes and continue working after that" in new Fixture {
      val account = Account(0, 100)
      val addr    = UInt256.Zero
      val value   = UInt256.MaxValue
      val code    = hex"deadbeefdeadbeefdeadbeef"

      def validateInitialWorld = (ws: WorldStateProxy[IO]) => {
        ws.accountExists(address1).unsafeRunSync() shouldBe true
        ws.accountExists(address2).unsafeRunSync() shouldBe true
        ws.getCode(address1).unsafeRunSync() shouldBe code
        ws.getStorage(address1).unsafeRunSync().load(addr).unsafeRunSync() shouldEqual value
        ws.getAccount(address1).unsafeRunSync().balance shouldBe 0
        ws.getAccount(address2).unsafeRunSync().balance shouldBe account.balance
      }

      // Update WS with some data
      val afterUpdatesWorldState = world
        .putAccount(address1, account)
        .putCode(address1, code)
        .putStorage(address1,
                    world
                      .getStorage(address1)
                      .unsafeRunSync()
                      .store(addr, value))
        .newEmptyAccount(address2)
        .transfer(address1, address2, UInt256(account.balance))
        .unsafeRunSync()

      validateInitialWorld(afterUpdatesWorldState)

      // Persist and check
      val persistedWorldState = afterUpdatesWorldState.persisted.unsafeRunSync()
      validateInitialWorld(persistedWorldState)

      // Create a new WS instance based on storages and new root state and check
      val newWorldState = history
        .getWorldStateProxy(-1, UInt256.Zero, Some(persistedWorldState.stateRootHash))
        .unsafeRunSync()
      validateInitialWorld(newWorldState)

      // Update this new WS check everything is ok
      val updatedNewWorldState = newWorldState
        .transfer(address2, address1, UInt256(account.balance))
        .unsafeRunSync()
      updatedNewWorldState.getAccount(address1).unsafeRunSync().balance shouldBe account.balance
      updatedNewWorldState.getAccount(address2).unsafeRunSync().balance shouldBe 0
      updatedNewWorldState.getStorage(address1).unsafeRunSync().load(addr).unsafeRunSync() shouldBe value

      // Persist and check again
      val persistedNewWorldState = updatedNewWorldState.persisted.unsafeRunSync()

      persistedNewWorldState.getAccount(address1).unsafeRunSync().balance shouldBe account.balance
      persistedNewWorldState.getAccount(address2).unsafeRunSync().balance shouldBe 0
      persistedNewWorldState.getStorage(address1).unsafeRunSync().load(addr).unsafeRunSync() shouldBe value
    }

    "not allow transfer to create empty accounts post EIP161" in new Fixture {
      val account         = Account(0, 100)
      val zeroTransfer    = UInt256.Zero
      val nonZeroTransfer = account.balance - 20

      val worldStateAfterEmptyTransfer = postEIP161WorldState
        .putAccount(address1, account)
        .transfer(address1, address2, zeroTransfer)
        .unsafeRunSync()

      worldStateAfterEmptyTransfer.getAccount(address1).unsafeRunSync().balance shouldBe account.balance
      worldStateAfterEmptyTransfer.getAccountOpt(address2).value.unsafeRunSync() shouldBe None

      val finalWorldState = worldStateAfterEmptyTransfer
        .transfer(address1, address2, nonZeroTransfer)
        .unsafeRunSync()

      finalWorldState.getAccount(address1).unsafeRunSync().balance shouldBe account.balance - nonZeroTransfer

      val secondAccount = finalWorldState.getAccount(address2).unsafeRunSync()
      secondAccount.balance shouldBe nonZeroTransfer
      secondAccount.nonce shouldBe UInt256.Zero
    }

    "update touched accounts using combineTouchedAccounts method" in new Fixture {
      val account         = Account(0, 100)
      val zeroTransfer    = UInt256.Zero
      val nonZeroTransfer = account.balance - 80

      val worldAfterSelfTransfer = postEIP161WorldState
        .putAccount(address1, account)
        .transfer(address1, address1, nonZeroTransfer)
        .unsafeRunSync()

      val worldStateAfterFirstTransfer = worldAfterSelfTransfer
        .putAccount(address1, account)
        .transfer(address1, address2, zeroTransfer)
        .unsafeRunSync()

      val worldStateAfterSecondTransfer = worldStateAfterFirstTransfer
        .transfer(address1, address3, nonZeroTransfer)
        .unsafeRunSync()

      val postEip161UpdatedWorld = postEIP161WorldState.combineTouchedAccounts(worldStateAfterSecondTransfer)

      postEip161UpdatedWorld.touchedAccounts should contain theSameElementsAs Set(address1, address3)
    }

    "correctly determine if account is dead" in new Fixture {
      val emptyAccountWorld = world.newEmptyAccount(address1)

      emptyAccountWorld.accountExists(address1).unsafeRunSync() shouldBe true
      emptyAccountWorld.isAccountDead(address1).unsafeRunSync() shouldBe true

      emptyAccountWorld.accountExists(address2).unsafeRunSync() shouldBe false
      emptyAccountWorld.isAccountDead(address2).unsafeRunSync() shouldBe true
    }

    "remove all ether from existing account" in new Fixture {
      val startValue = 100
      val account    = Account(UInt256.One, startValue)
      val code       = hex"deadbeefdeadbeefdeadbeef"

      val initialWorld           = world.putAccount(address1, account).persisted.unsafeRunSync()
      val worldAfterEtherRemoval = initialWorld.removeAllEther(address1).unsafeRunSync()
      val acc1                   = worldAfterEtherRemoval.getAccount(address1).unsafeRunSync()

      acc1.nonce shouldBe UInt256.One
      acc1.balance shouldBe UInt256.Zero
    }

    "get old state" in new Fixture {
      val w1 = history.getWorldStateProxy(0, 0, None).unsafeRunSync()

      val root = w1
        .putAccount(address1, Account(0, 1000))
        .persisted.unsafeRunSync().stateRootHash

      val w2=  history.getWorldStateProxy(0, 0, Some(root)).unsafeRunSync()
      println(w2.getAccountOpt(address1).value.unsafeRunSync())

      val root2 =
        w2
          .putAccount(address1, Account(0, 999))
          .putAccount(address2, Account(0, 998))
          .persisted.unsafeRunSync().stateRootHash

      val w3=  history.getWorldStateProxy(0, 0, Some(root2)).unsafeRunSync()
      println(w3.getAccountOpt(address1).value.unsafeRunSync())
      println(w3.getAccountOpt(address2).value.unsafeRunSync())


      println(w2.getAccountOpt(address1).value.unsafeRunSync())
      println(w2.getAccountOpt(address2).value.unsafeRunSync())
    }
  }
}
