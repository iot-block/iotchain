package jbok.core

import io.circe.generic.JsonCodec
import jbok.core.models._
import scodec.bits.ByteVector

package object messages {
  import jbok.codec.rlp.implicits._
  import jbok.codec.json.implicits._

  @JsonCodec final case class Status(chainId: BigInt, genesisHash: ByteVector, bestNumber: BigInt) {
    def isCompatible(other: Status): Boolean =
      chainId == other.chainId && genesisHash == other.genesisHash
  }

  object Status {
    val name = "Status"

    implicit val rlpCodec: RlpCodec[Status] = RlpCodec.gen[Status]
  }

  @JsonCodec final case class GetBlockHeadersByNumber(start: BigInt, limit: Int)
  object GetBlockHeadersByNumber {
    val name = "GetBlockHeadersByNumber"

    implicit val rlpCodec: RlpCodec[GetBlockHeadersByNumber] = RlpCodec.gen[GetBlockHeadersByNumber]
  }

  @JsonCodec final case class GetBlockHeadersByHash(start: ByteVector, limit: Int)
  object GetBlockHeadersByHash {
    val name = "GetBlockHeadersByNumber"

    implicit val rlpCodec: RlpCodec[GetBlockHeadersByHash] = RlpCodec.gen[GetBlockHeadersByHash]
  }

  @JsonCodec final case class BlockHeaders(headers: List[BlockHeader])
  object BlockHeaders {
    val name = "BlockHeaders"

    implicit val rlpCodec: RlpCodec[BlockHeaders] = RlpCodec.gen[BlockHeaders]
  }

  @JsonCodec final case class GetBlockBodies(hashes: List[ByteVector])
  object GetBlockBodies {
    val name = "GetBlockBodies"

    implicit val rlpCodec: RlpCodec[GetBlockBodies] = RlpCodec.gen[GetBlockBodies]
  }
  @JsonCodec final case class BlockBodies(bodies: List[BlockBody])
  object BlockBodies {
    val name = "BlockBodies"

    implicit val rlpCodec: RlpCodec[BlockBodies] = RlpCodec.gen[BlockBodies]
  }

  @JsonCodec final case class BlockHash(hash: ByteVector, number: BigInt)
  object BlockHash {
    val name = "BlockHash"

    implicit val rlpCodec: RlpCodec[BlockHash] = RlpCodec.gen[BlockHash]
  }

  @JsonCodec final case class NewBlockHashes(hashes: List[BlockHash])
  object NewBlockHashes {
    val name = "NewBlockHashes"

    implicit val rlpCodec: RlpCodec[NewBlockHashes] = RlpCodec.gen[NewBlockHashes]
  }

  @JsonCodec final case class NewBlock(block: Block)
  object NewBlock {
    val name = "NewBlock"

    implicit val rlpCodec: RlpCodec[NewBlock] = RlpCodec.gen[NewBlock]
  }

  @JsonCodec final case class SignedTransactions(txs: List[SignedTransaction])
  object SignedTransactions {
    val name = "SignedTransactions"

    implicit val rlpCodec: RlpCodec[SignedTransactions] = RlpCodec.gen[SignedTransactions]
  }

  @JsonCodec final case class AuthPacket(bytes: ByteVector)
  object AuthPacket {
    val name = "AuthPacket"

    implicit val rlpCodec: RlpCodec[AuthPacket] = RlpCodec.gen[AuthPacket]
  }
}
