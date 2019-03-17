package jbok.core.ledger

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.models.{Account, Address, UInt256}
import jbok.core.testkit._
import jbok.evm.WorldState
import jbok.persistent.KeyValueDB
import scodec.bits._

class WorldStateSpec extends JbokSpec {
  trait Fixture {
    import jbok.common.testkit._
    val history = History.forBackendAndPath[IO](KeyValueDB.INMEM, "").unsafeRunSync()

    val world = history
      .getWorldState(noEmptyAccounts = false)
      .unsafeRunSync()

    val postEIP161WorldState = history
      .getWorldState(noEmptyAccounts = true)
      .unsafeRunSync()

    val address1 = Address(0x123456)
    val address2 = Address(0xabcdef)
    val address3 = Address(0xfedcba)
  }

  "WorldState" should {
    "create then get account" in new Fixture {
      world.newEmptyAccount(address1).accountExists(address1).unsafeRunSync() shouldBe true
    }

    "put then get code" in new Fixture {
      forAll(genBoundedByteVector(0, 1024)) { code =>
        world.putCode(address1, code).getCode(address1).unsafeRunSync() shouldBe code
      }
    }

    "put then get storage" in new Fixture {
      val addr  = uint256Gen().sample.getOrElse(UInt256.MaxValue)
      val value = uint256Gen().sample.getOrElse(UInt256.MaxValue)

      val storage = world
        .getStorage(address1)
        .unsafeRunSync()
        .store(addr, value)
        .unsafeRunSync()

      world
        .putStorage(address1, storage)
        .getStorage(address1)
        .unsafeRunSync()
        .load(addr)
        .unsafeRunSync() shouldEqual value
    }

    "put then get original storage" in new Fixture {
      val addr     = UInt256.Zero
      val original = uint256Gen().sample.getOrElse(UInt256.MaxValue)
      val current  = uint256Gen().sample.getOrElse(UInt256.MaxValue)
      val account  = Account(0, 100)
      val world1   = world.putAccount(address1, account).persisted.unsafeRunSync()

      val originalStorage = world1
        .getStorage(address1)
        .unsafeRunSync()
        .store(addr, original)
        .unsafeRunSync()

      val world2 = world1.putStorage(address1, originalStorage).persisted.unsafeRunSync()

      val currentStorage = world1
        .getStorage(address1)
        .unsafeRunSync()
        .store(addr, current)
        .unsafeRunSync()

      world2
        .putStorage(address1, currentStorage)
        .getStorage(address1)
        .unsafeRunSync()
        .load(addr)
        .unsafeRunSync() shouldEqual current

      world2
        .putStorage(address1, currentStorage)
        .getOriginalStorage(address1)
        .unsafeRunSync()
        .load(addr)
        .unsafeRunSync() shouldEqual original
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
                        .store(UInt256.One, UInt256.Zero)
                        .unsafeRunSync())
          .persisted
          .unsafeRunSync()

      world1.stateRootHash shouldBe world2.stateRootHash
    }

    "storing a zero on a contract store position should remove it from the underlying tree" ignore new Fixture {
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
              .unsafeRunSync()
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
                        .store(UInt256.One, UInt256.Zero)
                        .unsafeRunSync())
          .persisted
          .unsafeRunSync()

      world1.stateRootHash shouldBe world3.stateRootHash
    }

    "be able to persist changes and continue working after that" in new Fixture {
      val account = Account(0, 100)
      val addr    = UInt256.Zero
      val value   = UInt256.MaxValue
      val code    = hex"deadbeefdeadbeefdeadbeef"

      def validateInitialWorld = (ws: WorldState[IO]) => {
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
                      .store(addr, value)
                      .unsafeRunSync())
        .newEmptyAccount(address2)
        .transfer(address1, address2, UInt256(account.balance))
        .unsafeRunSync()

      validateInitialWorld(afterUpdatesWorldState)

      // Persist and check
      val persistedWorldState = afterUpdatesWorldState.persisted.unsafeRunSync()
      validateInitialWorld(persistedWorldState)

      // Create a new WS instance based on storages and new root state and check
      val newWorldState = history
        .getWorldState(stateRootHash = Some(persistedWorldState.stateRootHash))
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
      val w1 = history.getWorldState().unsafeRunSync()

      val root = w1
        .putAccount(address1, Account(0, 1000))
        .persisted
        .unsafeRunSync()
        .stateRootHash

      val w2 = history.getWorldState().unsafeRunSync()

      val root2 =
        w2.putAccount(address1, Account(0, 999))
          .putAccount(address2, Account(0, 998))
          .persisted
          .unsafeRunSync()
          .stateRootHash

      val w3 = history.getWorldState(stateRootHash = Some(root2)).unsafeRunSync()
    }

    "create contract address" in new Fixture {
      val testcases: List[(Address, ByteVector, ByteVector, Address)] = List(
        (Address(hex"0x0000000000000000000000000000000000000000"),
         hex"0x0000000000000000000000000000000000000000000000000000000000000000",
         hex"0x00",
         Address(hex"0x4D1A2e2bB4F88F0250f26Ffff098B0b30B26BF38")),
        (Address(hex"0xdeadbeef00000000000000000000000000000000"),
         hex"0x0000000000000000000000000000000000000000000000000000000000000000",
         hex"0x00",
         Address(hex"0xB928f69Bb1D91Cd65274e3c79d8986362984fDA3")),
        (Address(hex"0xdeadbeef00000000000000000000000000000000"),
         hex"0x000000000000000000000000feed000000000000000000000000000000000000",
         hex"0x00",
         Address(hex"0xD04116cDd17beBE565EB2422F2497E06cC1C9833")),
        (Address(hex"0x0000000000000000000000000000000000000000"),
         hex"0x0000000000000000000000000000000000000000000000000000000000000000",
         hex"0xdeadbeef",
         Address(hex"0x70f2b2914A2a4b783FaEFb75f459A580616Fcb5e")),
        (Address(hex"0x00000000000000000000000000000000deadbeef"),
         hex"0x00000000000000000000000000000000000000000000000000000000cafebabe",
         hex"0xdeadbeef",
         Address(hex"0x60f3f640a8508fC6a86d45DF051962668E1e8AC7")),
        (Address(hex"0x00000000000000000000000000000000deadbeef"),
         hex"0x00000000000000000000000000000000000000000000000000000000cafebabe",
         hex"0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
         Address(hex"0x1d8bfDC5D46DC4f61D6b6115972536eBE6A8854C")),
        (Address(hex"0x0000000000000000000000000000000000000000"),
         hex"0x0000000000000000000000000000000000000000000000000000000000000000",
         hex"0x",
         Address(hex"0xE33C0C7F7df4809055C3ebA6c09CFe4BaF1BD9e0"))
      )
      val w1 = history.getWorldState().unsafeRunSync()

      testcases.foreach {
        case (address, salt, initCode, expected) =>
          w1.createContractAddressWithSalt(address, salt, initCode)
            .unsafeRunSync() shouldBe expected
      }
    }
  }
}
