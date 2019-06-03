package jbok.core.config

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._
import jbok.common.math.N
import jbok.common.math.implicits._
import jbok.core.consensus.poa.clique.Clique
import jbok.core.models._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import scodec.bits._

@ConfiguredJsonCodec
final case class GenesisConfig(
                                chainId: ChainId = ChainId(0),
                                alloc: Map[Address, N] = Map.empty,
                                miners: List[Address] = Nil,
                                timestamp: Long = 0L,
                                coinbase: Address = Address.empty,
                                difficulty: N = N(0),
                                gasLimit: N = N("16716680"),
) {
  lazy val header = BlockHeader(
    parentHash = ByteVector.empty,
    beneficiary = coinbase.bytes,
    stateRoot = MerklePatriciaTrie.emptyRootHash,
    transactionsRoot = MerklePatriciaTrie.emptyRootHash,
    receiptsRoot = MerklePatriciaTrie.emptyRootHash,
    logsBloom = ByteVector.empty,
    difficulty = difficulty,
    number = 0,
    gasLimit = gasLimit,
    gasUsed = 0,
    unixTimestamp = timestamp,
    extra = Clique.fillExtraData(miners)
  )

  lazy val body = BlockBody(Nil)

  lazy val block = Block(header, body)
}
