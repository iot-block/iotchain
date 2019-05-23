package jbok.core.messages

import io.circe.generic.JsonCodec
import jbok.codec.rlp.implicits._
import jbok.codec.json.implicits._
import jbok.core.models.Block
import scodec.bits.ByteVector

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
