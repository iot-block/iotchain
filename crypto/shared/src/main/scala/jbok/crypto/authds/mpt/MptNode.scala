package jbok.crypto.authds.mpt

import jbok.codec.HexPrefix
import jbok.codec.HexPrefix.Nibbles
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.crypto._
import scodec.Attempt.{Failure, Successful}
import scodec._
import scodec.bits.ByteVector
import scodec.codecs._

sealed abstract class MptNode {
  import MptNode._

  lazy val hash: ByteVector = bytes.kec256

  lazy val bytes: ByteVector = nodeCodec.encode(this).require.bytes

  lazy val capped: ByteVector = if (bytes.length < HashLength) bytes else hash

  lazy val entry: Either[ByteVector, MptNode] = if (capped.length == HashLength) Left(hash) else Right(this)
}

object MptNode {
  val HashLength = 32
  type Hash      = ByteVector
  type NodeEntry = Either[Hash, MptNode]

  object NodeEntry {
    def apply(node: MptNode): NodeEntry =
      if (node.bytes.length < HashLength) Right(node)
      else Left(node.hash)
  }

  case class LeafNode(key: Nibbles, value: ByteVector)     extends MptNode
  case class ExtensionNode(key: Nibbles, child: NodeEntry) extends MptNode
  case class BranchNode(branches: List[Option[NodeEntry]], value: Option[ByteVector]) extends MptNode {
    def activated: List[(Int, Option[NodeEntry])] =
      branches.zipWithIndex
        .filter(_._1.isDefined)
        .map(_.swap)

    def branchAt(char: Char): Option[NodeEntry] =
      branches(Integer.parseInt(char.toString, 16))

    def updateValue(v: ByteVector): BranchNode =
      this.copy(value = Some(v))

    def updateBranch(char: Char, entry: Option[NodeEntry]): BranchNode =
      this.copy(branches = this.branches.updated(Integer.parseInt(char.toString, 16), entry))
  }
  object BranchNode {
    def withSingleBranch(char: Char, child: NodeEntry, value: Option[ByteVector] = None): BranchNode = {
      val node = BranchNode.withOnlyValue(value)
      node.updateBranch(char, Some(child))
    }

    def withOnlyValue(value: Option[ByteVector]): BranchNode = {
      val branches = List.fill[Option[NodeEntry]](16)(None)
      BranchNode(branches, value)
    }

    def withOnlyValue(value: ByteVector): BranchNode =
      withOnlyValue(Some(value))
  }

  private lazy val entryCodec: Codec[NodeEntry] =
    bytes.narrow[Either[ByteVector, MptNode]](
      bytes =>
        bytes.size match {
          case size if size < 32 =>
            nodeCodec.decode(bytes.bits).map(_.value).map(Right.apply)
          case size if size == 32 =>
            Successful(Left(bytes))
          case size =>
            Failure(Err(s"unexpected capped node size (size: $size, bytes: $bytes)"))
      }, {
        case Left(hash)  => hash
        case Right(node) => nodeCodec.encode(node).require.bytes
      }
    )

  private lazy val entryOptCodec: Codec[Option[NodeEntry]] =
    bytes.narrow[Option[NodeEntry]](
      bytes =>
        bytes.size match {
          case 0 => Successful(None)
          case _ => Successful(Some(entryCodec.decode(bytes.bits).require.value))
      }, {
        case None    => ByteVector.empty
        case Some(n) => entryCodec.encode(n).require.bytes
      }
    )

  implicit lazy val nodeCodec: RlpCodec[MptNode] = RlpCodec.pure(
    rbyteslist.codec
      .narrow[MptNode](
        list =>
          list.size match {
            case 2 =>
              HexPrefix.decode(list.head) map {
                case (true, k)  => LeafNode(k, list(1))
                case (false, k) => ExtensionNode(k, entryCodec.decode(list(1).bits).require.value)
              }

            case 17 =>
              list.splitAt(16) match {
                case (branches, value) =>
                  branches
                    .foldLeft(Attempt.successful(List[Option[NodeEntry]]())) {
                      case (attempt, branch) =>
                        attempt.flatMap(entries => entryOptCodec.decode(branch.bits).map(r => entries :+ r.value))
                    }
                    .flatMap { nodes =>
                      bytes
                        .decode(value.head.bits)
                        .map(r =>
                          r.value match {
                            case ByteVector.empty => BranchNode(nodes, None)
                            case v                => BranchNode(nodes, Some(v))
                        })
                    }
              }

            case size => Failure(Err(s"invalid node list size $size"))
        }, {
          case LeafNode(k, v) =>
            List(HexPrefix.encode(k, isLeaf = true), v)
          case ExtensionNode(k, v) =>
            List(HexPrefix.encode(k, isLeaf = false), entryCodec.encode(v).require.bytes)
          case BranchNode(branches, v) =>
            branches.map(b => entryOptCodec.encode(b).require.bytes) ++ List(v.getOrElse(ByteVector.empty))
        }
      ))
}
