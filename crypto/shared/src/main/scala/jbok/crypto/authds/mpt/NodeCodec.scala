package jbok.crypto.authds.mpt

import jbok.codec.HexPrefix
import jbok.codec.rlp.RlpCodec
import jbok.crypto.authds.mpt.Node._
import scodec.Attempt.{Failure, Successful}
import scodec._
import scodec.bits._
import scodec.codecs._
import jbok.codec.rlp.codecs._

object NodeCodec {
  def encode(node: Node): Attempt[ByteVector] =
    nodeCodec.encode(node).map(_.bytes)

  def decode(bytes: ByteVector): Attempt[Node] = nodeCodec.decode(bytes.bits).map(_.value)

  private[jbok] lazy val entryCodec: Codec[NodeEntry] =
    bytes.narrow[Either[ByteVector, Node]](
      bytes =>
        bytes.size match {
          case size if size < 32 =>
            decode(bytes).map(Right.apply)
          case size if size == 32 =>
            Successful(Left(bytes))
          case size =>
            Failure(Err(s"unexpected capped node size (size: $size, bytes: $bytes)"))
      }, {
        case Left(hash)  => hash
        case Right(node) => nodeCodec.encode(node).require.bytes
      }
    )

  private[jbok] lazy val entryOptCodec: Codec[Option[NodeEntry]] =
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

  implicit private[jbok] lazy val nodeCodec: RlpCodec[Node] = RlpCodec.pure(
    rbyteslist.codec
      .narrow[Node](
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
