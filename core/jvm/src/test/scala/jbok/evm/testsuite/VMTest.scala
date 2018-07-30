package jbok.evm.testsuite


import cats.effect.IO
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import jbok.core.models.{Account, Address, BlockHeader, UInt256}
import jbok.core.store.EvmCodeStore
import jbok.crypto._
import jbok.crypto.authds.mpt.{MPTrie, MPTrieStore}
import jbok.evm._
import jbok.persistent.{KeyValueDB, SnapshotKeyValueStore}
import org.scalatest.{Matchers, WordSpec}
import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.json._
import scala.io.Source
import jbok.codec.codecs._
import testsuite._
import better.files._

//Env           stEnv                 `json:"env"`
//Exec          vmExec                `json:"exec"`
//Logs          common.UnprefixedHash `json:"logs"`
//GasRemaining  *math.HexOrDecimal64  `json:"gas"`
//Out           hexutil.Bytes         `json:"out"`
//Pre           core.GenesisAlloc     `json:"pre"`
//Post          core.GenesisAlloc     `json:"post"`
//PostStateRoot common.Hash           `json:"postStateRoot"`

case class VMJson(
    _info: InfoJson,
    callcreates: List[CallCreateJson],
    env: EnvJson,
    exec: ExecJson,
    gas: BigInt,
    logs: ByteVector,
    out: ByteVector,
    post: Map[Address, PrePostJson],
    pre: Map[Address, PrePostJson]
)

//currentCoinbase: The current block’s coinbase address, to be returned by the COINBASE instruction.
//currentDifficulty: The current block’s difficulty, to be returned by the DIFFICULTY instruction.
//currentGasLimit: The current block’s gas limit.
//currentNumber: The current block’s number.
//currentTimestamp: The current block’s timestamp.
//previousHash: The previous block’s hash.

case class EnvJson(
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

case class PrePostJson(balance: BigInt, nonce: BigInt, code: ByteVector, storage: Map[ByteVector, ByteVector])

//address: The address of the account under which the code is executing, to be returned by the ADDRESS instruction.
//origin: The address of the execution’s origin, to be returned by the ORIGIN instruction.
//caller: The address of the execution’s caller, to be returned by the CALLER instruction.
//value: The value of the call (or the endowment of the create), to be returned by the CALLVALUE instruction.
//data: The input data passed to the execution, as used by the CALLDATA... instructions. Given as an array of byte values. See $DATA_ARRAY.
//code: The actual code that should be executed on the VM (not the one stored in the state(address)) . See $DATA_ARRAY.
//gasPrice: The price of gas for the transaction, as used by the GASPRICE instruction.
//gas: The total amount of gas available for the execution, as would be returned by the GAS instruction were it be executed first.

case class ExecJson(
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

case class CallCreateJson(data: ByteVector, destination: ByteVector, gasLimit: BigInt, value: BigInt)


case class InfoJson(comment: String, filledwith: String, lllcversion: String, source: String, sourceHash: String)

class VMTest extends WordSpec with Matchers {
  def loadMockWorldState(json: Map[Address, PrePostJson], currentNumber: BigInt): WorldStateProxy[IO] = {
    val accounts = json.map {
      case (addr, account) => (addr, Account(balance = UInt256(account.balance), nonce = UInt256(account.nonce)))
    }

    val accountCodes = json.map {
      case (addr, account) => (addr, account.code)
    }

    val db = KeyValueDB.inMemory[IO].unsafeRunSync()

    val storages = json.map {
      case (addr, account) => (addr, Storage.fromMap[IO](account.storage.map(s => (UInt256(s._1), UInt256(s._2)))).unsafeRunSync())
    }

    val mpt = MPTrieStore[IO, Address, Account](db).unsafeRunSync()
    val accountProxy = SnapshotKeyValueStore[IO, Address, Account](mpt) ++ accounts

    WorldStateProxy[IO](
      db,
      accountProxy,
      Set.empty,
      accountCodes,
      storages,
      new EvmCodeStore[IO](db),
      MPTrie.emptyRootHash,
      UInt256.Zero,
      noEmptyAccounts = true,
      number => IO(Some(UInt256(ByteVector(number.toString.getBytes).kec256)))
    )
  }

  "evm test suite" should {
    "load and run official json test files" in {
      val file = File(Resource.getUrl("VMTests"))
      val fileList = file.listRecursively.filter(f => f.name.endsWith(".json") &&
        !f.path.toString.contains("vmPerformance")).toList

      val sources = for {
        file <- fileList
        lines = file.lines.mkString("\n")
      } yield lines

      for {
        source <- sources
        caseJson: Json = parse(source).getOrElse(Json.Null)
        testCase <- caseJson.as[Map[String, VMJson]].getOrElse(Map.empty)
      } {
        val label = testCase._1
        val vmJson = testCase._2
        val config = EvmConfig.HomesteadConfigBuilder(None)

        val preState = loadMockWorldState(vmJson.pre, vmJson.env.currentNumber)

        val postState = loadMockWorldState(vmJson.post, vmJson.env.currentNumber)

        val currentBlockHeader = BlockHeader(
          ByteVector.empty,
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
          ByteVector.empty,
          ByteVector.empty,
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
          0
        )

        val context = ProgramContext(env, vmJson.exec.address, vmJson.exec.gas, preState, config)

        println(label)
        val result = VM.run(context).unsafeRunSync()

        val world = if (result.addressesToDelete.nonEmpty) {
          result.world.accountCodes.filter(!_._2.isEmpty) - result.addressesToDelete.head shouldEqual postState.accountCodes.filter(!_._2.isEmpty)
          result.world.deleteAccount(result.addressesToDelete.head)
        } else {
          result.world.accountCodes.filter(!_._2.isEmpty) shouldEqual postState.accountCodes.filter(!_._2.isEmpty)
          result.world
        }

        result.gasRemaining shouldEqual vmJson.gas
        world.accountProxy.toMap.unsafeRunSync() shouldEqual postState.accountProxy.toMap.unsafeRunSync()
        for {
          contractStorages <- postState.contractStorages
          address = contractStorages._1
          storage = contractStorages._2.data.unsafeRunSync()
          if storage.nonEmpty
        } {
          world.contractStorages.get(address).map(_.data.unsafeRunSync() shouldEqual storage)
        }

        result.returnData shouldEqual vmJson.out
        Codec.encode(result.logs).require.bytes.kec256 shouldEqual vmJson.logs

        println(label + " test successful.")
      }
    }
  }
}
