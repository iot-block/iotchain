package jbok.core.messages

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.rlp.implicits._
import jbok.codec.json.implicits._
import jbok.common.math.N
import jbok.core.models.Block
import scodec.bits.ByteVector

@ConfiguredJsonCodec final case class BlockHash(hash: ByteVector, number: N)
object BlockHash {
  val name = "BlockHash"

  implicit val rlpCodec: RlpCodec[BlockHash] = RlpCodec.gen[BlockHash]
}

@ConfiguredJsonCodec final case class NewBlockHashes(hashes: List[BlockHash])
object NewBlockHashes {
  val name = "NewBlockHashes"

  implicit val rlpCodec: RlpCodec[NewBlockHashes] = RlpCodec.gen[NewBlockHashes]
}

@ConfiguredJsonCodec final case class NewBlock(block: Block)
object NewBlock {
  val name = "NewBlock"

  implicit val rlpCodec: RlpCodec[NewBlock] = RlpCodec.gen[NewBlock]
}
