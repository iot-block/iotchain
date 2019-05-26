package jbok.evm.testsuite

import better.files._
import cats.effect.IO
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import jbok.codec.json.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.ledger.History
import jbok.core.models.{Account, Address, BlockHeader, UInt256}
import jbok.core.store.ColumnFamilies
import jbok.crypto._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.evm._
import jbok.persistent.{MemoryKVStore, StageKVStore}
import scodec.bits.ByteVector
import jbok.core.CoreSpec

import scala.collection.JavaConverters._

//Env           stEnv                 `json:"env"`
//Exec          vmExec                `json:"exec"`
//Logs          common.UnprefixedHash `json:"logs"`
//GasRemaining  *math.HexOrDecimal64  `json:"gas"`
//Out           hexutil.Bytes         `json:"out"`
//Pre           core.GenesisAlloc     `json:"pre"`
//Post          core.GenesisAlloc     `json:"post"`
//PostStateRoot common.Hash           `json:"postStateRoot"`

final case class VMJson(
    _info: InfoJson,
    env: EnvJson,
    exec: ExecJson,
    pre: Map[Address, PrePostJson],
    callcreates: Option[List[CallCreateJson]],
    gas: Option[BigInt],
    logs: Option[ByteVector],
    out: Option[ByteVector],
    post: Option[Map[Address, PrePostJson]]
)

//currentCoinbase: The current block’s coinbase address, to be returned by the COINBASE instruction.
//currentDifficulty: The current block’s difficulty, to be returned by the DIFFICULTY instruction.
//currentGasLimit: The current block’s gas limit.
//currentNumber: The current block’s number.
//currentTimestamp: The current block’s timestamp.
//previousHash: The previous block’s hash.

final case class EnvJson(
    currentCoinbase: Address,
    currentDifficulty: BigInt,
    currentGasLimit: BigInt,
    currentNumber: BigInt,
    currentTimestamp: BigInt
)

//balance: The balance of the account.
//nonce: The nonce of the account.
//code: The body code of the account, given as an array of byte values. See $DATA_ARRAY.
//storage: The account’s storage, given as a mapping of keys to values. For key used notion of string as digital or hex number e.g: "1200" or "0x04B0" For values used $DATA_ARRAY.

final case class PrePostJson(balance: BigInt, nonce: BigInt, code: ByteVector, storage: Map[ByteVector, ByteVector])

//address: The address of the account under which the code is executing, to be returned by the ADDRESS instruction.
//origin: The address of the execution’s origin, to be returned by the ORIGIN instruction.
//caller: The address of the execution’s caller, to be returned by the CALLER instruction.
//value: The value of the call (or the endowment of the create), to be returned by the CALLVALUE instruction.
//data: The input data passed to the execution, as used by the CALLDATA... instructions. Given as an array of byte values. See $DATA_ARRAY.
//code: The actual code that should be executed on the VM (not the one stored in the state(address)) . See $DATA_ARRAY.
//gasPrice: The price of gas for the transaction, as used by the GASPRICE instruction.
//gas: The total amount of gas available for the execution, as would be returned by the GAS instruction were it be executed first.

final case class ExecJson(
    address: Address,
    origin: Address,
    caller: Address,
    value: BigInt,
    data: ByteVector,
    code: ByteVector,
    gasPrice: BigInt,
    gas: BigInt
)

//data: An array of bytes specifying the data with which the CALL or CREATE operation was made. In the case of CREATE, this would be the (initialisation) code. See $DATA_ARRAY.
//destination: The receipt address to which the CALL was made, or the null address ("0000...") if the corresponding operation was CREATE.
//gasLimit: The amount of gas with which the operation was made.
//value: The value or endowment with which the operation was made.

final case class CallCreateJson(data: ByteVector, destination: ByteVector, gasLimit: BigInt, value: BigInt)

final case class InfoJson(comment: String, filledwith: String, lllcversion: String, source: String, sourceHash: String)

class VMTest extends CoreSpec {
  implicit val bigIntDecoder: Decoder[BigInt] = Decoder[String].map[BigInt](
    x =>
      if (x.startsWith("0x"))
        BigInt(x.substring(2, x.length), 16)
      else
        BigInt(x))

  implicit val bigIntEncoder: Encoder[BigInt] = Encoder[String].contramap[BigInt](_.toString(10))

  def loadMockWorldState(json: Map[Address, PrePostJson], currentNumber: BigInt): WorldState[IO] = {
    val accounts = json.map {
      case (addr, account) => (addr, Account(balance = UInt256(account.balance), nonce = UInt256(account.nonce)))
    }

    val accountCodes = json.map {
      case (addr, account) => (addr, account.code)
    }

    val store      = MemoryKVStore[IO].unsafeRunSync()
    val history = History(store)

    val storages = json.map {
      case (addr, account) =>
        (addr, Storage.fromMap[IO](account.storage.map(s => (UInt256(s._1), UInt256(s._2)))).unsafeRunSync())
    }

    val mpt          = MerklePatriciaTrie[IO, Address, Account](ColumnFamilies.Node, store).unsafeRunSync()
    val accountProxy = StageKVStore(mpt) ++ accounts

    WorldState[IO](
      store,
      history,
      accountProxy,
      MerklePatriciaTrie.emptyRootHash,
      Set.empty,
      storages,
      accountCodes,
      UInt256.Zero,
      noEmptyAccounts = true
    )
  }

  def check(label: String, vmJson: VMJson) =
    s"pass test suite ${label}" in {
      val config   = EvmConfig.HomesteadConfigBuilder(None)
      val preState = loadMockWorldState(vmJson.pre, vmJson.env.currentNumber)
      val currentBlockHeader = BlockHeader(
        ByteVector.empty,
        vmJson.env.currentCoinbase.bytes,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        vmJson.env.currentDifficulty,
        vmJson.env.currentNumber,
        vmJson.env.currentGasLimit,
        BigInt(0),
        vmJson.env.currentTimestamp.toLong,
        ByteVector.empty
      )
      val env = ExecEnv(
        vmJson.exec.address,
        vmJson.exec.caller,
        vmJson.exec.origin,
        UInt256(vmJson.exec.gasPrice),
        vmJson.exec.data,
        UInt256(vmJson.exec.value),
        Program(vmJson.exec.code),
        currentBlockHeader,
        0,
        noSelfCall = true
      )
      val context = ProgramContext(env, vmJson.exec.address, vmJson.exec.gas, preState, config)

      val result = VM.run(context).unsafeRunSync()

      vmJson.post.foreach { post =>
        val postState = loadMockWorldState(post, vmJson.env.currentNumber)
        val world = if (result.addressesToDelete.nonEmpty) {
          result.world.contractCodes
            .filter(!_._2.isEmpty) - result.addressesToDelete.head shouldEqual postState.contractCodes.filter(!_._2.isEmpty)
          result.world.delAccount(result.addressesToDelete.head)
        } else {
          result.world.contractCodes.filter(!_._2.isEmpty) shouldEqual postState.contractCodes.filter(!_._2.isEmpty)
          result.world
        }

        world.accountProxy.toMap.unsafeRunSync() shouldEqual postState.accountProxy.toMap.unsafeRunSync()
        for {
          contractStorages <- postState.contractStorages
          address = contractStorages._1
          storage = contractStorages._2.data.unsafeRunSync()
          if storage.nonEmpty
        } {
          world.contractStorages.get(address).map(_.data.unsafeRunSync()).getOrElse(Map.empty[UInt256, UInt256]) shouldEqual storage
        }
      }

      vmJson.gas.foreach(_ shouldBe result.gasRemaining)
      vmJson.out.foreach(_ shouldBe result.returnData)
      vmJson.logs.foreach(_ shouldBe result.logs.asValidBytes.kec256)

      if (vmJson.gas.isEmpty) {
        vmJson.post.isEmpty shouldBe true
        vmJson.out.isEmpty shouldBe true
        vmJson.logs.isEmpty shouldBe true
        result.error.nonEmpty shouldBe true
      } else {
        result.error.isEmpty shouldBe true
      }
    }

  "load and run official json test files" should {
    val file = File(Resource.getUrl("VMTests"))
    val fileList = file.listRecursively
      .filter(
        f =>
          f.name.endsWith(".json") &&
            !f.path.toString.contains("vmPerformance"))
      .toList

    val sources = for {
      file <- fileList
      lines = file.lines.mkString("\n")
    } yield file.path.iterator().asScala.toList.takeRight(2).mkString("/") -> lines

    for {
      (name, json) <- sources
      caseJson: Json = parse(json).getOrElse(Json.Null)
      (label, vmJson) <- caseJson.as[Map[String, VMJson]].right.get
    } {
      check(s"${name}: ${label}", vmJson)
    }
  }
}
