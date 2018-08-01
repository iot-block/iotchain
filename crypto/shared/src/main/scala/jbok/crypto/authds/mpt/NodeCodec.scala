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
    (node match {
      case BlankNode => nodeCodec.encode(node)
      case _         => nonEmptyCodec.encode(node)
    }).map(_.bytes)

  def decode(bytes: ByteVector): Attempt[Node] = nodeCodec.decode(bytes.bits).map(_.value)

  private[jbok] val nodeCodec: RlpCodec[Node] = RlpCodec.pure(
    recover(rempty.codec).consume[Node] { empty =>
      if (empty) provide(BlankNode)
      else nonEmptyCodec.codec
    } { node =>
      node == BlankNode
    }
  )

  private[jbok] lazy val nonEmptyCodec: RlpCodec[Node] = RlpCodec.pure(
    rbyteslist.codec.narrow[Node](
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
                  .foldLeft(Attempt.successful(List[NodeEntry]())) {
                    case (attempt, branch) =>
                      attempt.flatMap(entries => entryCodec.decode(branch.bits).map(r => entries :+ r.value))
                  }
                  .flatMap { nodes =>
                    roptional[ByteVector].decode(value.head.bits).map(r => BranchNode(nodes, r.value))
                  }
            }

          case size => Failure(Err(s"invalid node list size $size"))
      }, {
        case LeafNode(k, v) =>
          HexPrefix.encode(k, isLeaf = true) :: v :: Nil
        case ExtensionNode(k, v) =>
          HexPrefix.encode(k, isLeaf = false) :: entryCodec.encode(v).require.bytes :: Nil
        case BranchNode(branches, v) =>
          branches.map(b => entryCodec.encode(b).require.bytes) ++ List(roptional[ByteVector].encode(v).require.bytes)
        case BlankNode => ByteVector.empty :: Nil
      }
    )
  )

  private[jbok] lazy val entryCodec: RlpCodec[NodeEntry] =
    RlpCodec.pure(
      rbytes.codec.narrow[Either[ByteVector, Node]](
        bytes =>
          bytes.size match {
            case 0 =>
              Successful(Right(BlankNode))
            case size if size < 32 =>
              decode(bytes).map(Right.apply)
            case size if size == 32 =>
              Successful(Left(bytes))
            case size =>
              Failure(Err(s"unexpected capped node size (size: $size, bytes: $bytes)"))
        }, {
          case Left(hash)  => hash
          case Right(node) => encode(node).require
        }
      )
    )
}
