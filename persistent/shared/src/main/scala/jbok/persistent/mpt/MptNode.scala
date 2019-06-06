package jbok.persistent.mpt

import cats.implicits._
import jbok.codec.HexPrefix
import jbok.codec.HexPrefix.Nibbles
import jbok.codec.rlp.{RlpCodec, RlpEncoded}
import jbok.codec.rlp.implicits._
import jbok.crypto._
import scodec._
import scodec.bits.{BitVector, ByteVector}

sealed abstract class MptNode {
  import MptNode._

  lazy val hash: ByteVector = bytes.bytes.kec256

  lazy val bytes: RlpEncoded = this.encoded

  lazy val capped: ByteVector = if (bytes.bytes.length < HashLength) bytes.bytes else hash

  lazy val entry: Either[ByteVector, MptNode] = if (capped.length == HashLength) Left(hash) else Right(this)
}

object MptNode {
  val HashLength = 32
  type NodeEntry = Either[ByteVector, MptNode]

  final case class LeafNode(key: Nibbles, value: RlpEncoded)     extends MptNode
  final case class ExtensionNode(key: Nibbles, child: NodeEntry) extends MptNode
  final case class BranchNode(branches: List[Option[NodeEntry]], value: Option[RlpEncoded]) extends MptNode {
    def activated: List[(Int, Option[NodeEntry])] =
      branches.zipWithIndex
        .filter(_._1.isDefined)
        .map(_.swap)

    def branchAt(char: Char): Option[NodeEntry] =
      branches(Integer.parseInt(char.toString, 16))

    def updateValue(v: RlpEncoded): BranchNode =
      this.copy(value = Some(v))

    def updateBranch(char: Char, entry: Option[NodeEntry]): BranchNode =
      this.copy(branches = this.branches.updated(Integer.parseInt(char.toString, 16), entry))
  }

  object BranchNode {
    def withSingleBranch(char: Char, child: NodeEntry, value: Option[RlpEncoded] = None): BranchNode = {
      val node = BranchNode.withOnlyValue(value)
      node.updateBranch(char, Some(child))
    }

    def withOnlyValue(value: Option[RlpEncoded]): BranchNode = {
      val branches = List.fill[Option[NodeEntry]](16)(None)
      BranchNode(branches, value)
    }

    def withOnlyValue(value: RlpEncoded): BranchNode = {
      val branches = List.fill[Option[NodeEntry]](16)(None)
      BranchNode(branches, Some(value))
    }
  }

  implicit val entryCodec: RlpCodec[NodeEntry] = new RlpCodec[NodeEntry] {
    override def decode(bits: BitVector): Attempt[DecodeResult[NodeEntry]] =
      if (RlpCodec.isItemPrefix(bits)) {
        RlpCodec[ByteVector].decode(bits).map(_.map(_.asLeft))
      } else {
        nodeCodec.decode(bits).map(_.map(_.asRight))
      }

    override def encode(value: NodeEntry): Attempt[BitVector] =
      value match {
        case Left(hash)  => RlpCodec[ByteVector].encode(hash)
        case Right(node) => nodeCodec.encode(node)
      }
  }

  implicit val optionalEntryCodec: RlpCodec[Option[NodeEntry]] =
    RlpCodec.optionAsNull(entryCodec)

  implicit val optionalValueCodec: RlpCodec[Option[RlpEncoded]] =
    RlpCodec.optionAsNull(RlpCodec.rlpCodec)

  implicit val nodeCodec: RlpCodec[MptNode] = new RlpCodec[MptNode] {
    override def decode(bits: BitVector): Attempt[DecodeResult[MptNode]] =
      RlpCodec[List[RlpEncoded]].decode(bits).flatMap { result =>
        result.value match {
          case key :: value :: Nil =>
            Attempt.fromEither[DecodeResult[MptNode]] {
              for {
                keyBytes          <- key.decoded[ByteVector].leftMap(e => Err(e.getMessage))
                (isLeaf, nibbles) <- HexPrefix.decode(keyBytes).toEither
                node: MptNode <- if (isLeaf) {
                  Right(LeafNode(nibbles, value))
                } else {
                  value
                    .decoded[NodeEntry]
                    .leftMap(e => Err(e.getMessage))
                    .map(entry => ExtensionNode(nibbles, entry))
                }
              } yield DecodeResult(node, result.remainder)
            }

          case list if list.length == 17 =>
            list.splitAt(16) match {
              case (branches, value :: Nil) =>
                Attempt.fromEither {
                  for {
                    nodes    <- branches.map(_.decoded[Option[NodeEntry]]).sequence.leftMap(e => Err(e.getMessage))
                    valueOpt <- value.decoded[Option[RlpEncoded]].leftMap(e => Err(e.getMessage))
                  } yield DecodeResult(BranchNode(nodes, valueOpt), result.remainder)
                }

              case _ => Attempt.failure(Err("incorrect mpt node bytes"))
            }

          case list => Attempt.failure(Err(s"invalid mpt node list length ${list.length}"))
        }
      }

    override def encode(value: MptNode): Attempt[BitVector] = value match {
      case LeafNode(k, v)          => RlpCodec[List[RlpEncoded]].encode(List(HexPrefix.encode(k, isLeaf = true).encoded, v))
      case ExtensionNode(k, v)     => RlpCodec[List[RlpEncoded]].encode(List(HexPrefix.encode(k, isLeaf = false).encoded, v.encoded))
      case BranchNode(branches, v) => RlpCodec[List[RlpEncoded]].encode(branches.map(x => x.encoded) ++ List(v.getOrElse(RlpEncoded.emptyItem)))
    }

    override def sizeBound: SizeBound = SizeBound.unknown
  }
}
