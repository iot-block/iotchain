package jbok.core.models

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.common.math.N
import scodec.bits._
import scodec.codecs

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Receipt")
@JSExportAll
@ConfiguredJsonCodec
final case class Receipt(
    postTransactionStateHash: ByteVector,
    cumulativeGasUsed: N,
    logsBloomFilter: ByteVector,
    logs: List[TxLogEntry],
    txHash: ByteVector,
    contractAddress: Option[Address],
    gasUsed: N,
    status: Boolean
)

object Receipt {
  implicit val optionAddressCodec: RlpCodec[Option[Address]] =
    RlpCodec.rlp {
      codecs.bytes.xmap[Option[Address]]({ bytes =>
        if (bytes.isEmpty) None
        else Some(Address(bytes))
      }, {
        case Some(address) => address.bytes
        case None          => ByteVector.empty
      })
    }

  implicit val receiptCodec: RlpCodec[Receipt] = RlpCodec.gen[Receipt]
}
