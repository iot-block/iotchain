package jbok.crypto.authds.mpt

import jbok.codec.HexPrefix.Nibbles
import jbok.crypto._
import scodec.bits.ByteVector

sealed abstract class Node {
  lazy val hash: ByteVector = bytes.kec256

  lazy val bytes: ByteVector = NodeCodec.encode(this).require

  lazy val capped: ByteVector = if (bytes.length < 32) bytes else hash

  lazy val entry: Either[ByteVector, Node] = if (capped.length == 32) Left(hash) else Right(this)
}

object Node {
  type Hash = ByteVector
  type NodeEntry = Either[Hash, Node]

  object NodeEntry {
    def apply(node: Node): NodeEntry =
      if (node.bytes.length < 32) Right(node)
      else Left(node.hash)
  }

  case object BlankNode extends Node
  case class LeafNode(key: Nibbles, value: ByteVector) extends Node
  case class ExtensionNode(key: Nibbles, child: NodeEntry) extends Node
  case class BranchNode(branches: List[NodeEntry], value: Option[ByteVector]) extends Node {
    def activated: List[(String, NodeEntry)] =
      branches.zipWithIndex
        .filter(_._1 != Right(BlankNode))
        .map { case (n, i) => MPTrie.alphabet(i) -> n }

    def branchAt(char: Char): NodeEntry =
      branches(Integer.parseInt(char.toString, 16))

    def updateValue(v: ByteVector): BranchNode =
      this.copy(value = Some(v))

    def updateBranch(char: Char, entry: Either[ByteVector, Node]): BranchNode =
      this.copy(branches = this.branches.updated(Integer.parseInt(char.toString, 16), entry))

    override def toString: String = s"""BranchNode(${activated}, ${value})"""
  }

  object BranchNode {
    def empty: BranchNode = withOnlyValue(None)

    def withSingleBranch(char: Char, child: NodeEntry, value: Option[ByteVector] = None): BranchNode = {
      val node = BranchNode.withOnlyValue(value)
      node.updateBranch(char, child)
    }

    def withOnlyValue(value: Option[ByteVector]): BranchNode = {
      val branches = List.fill[Either[ByteVector, Node]](16)(Right(BlankNode))
      BranchNode(branches, value)
    }

    def withOnlyValue(value: ByteVector): BranchNode =
      withOnlyValue(Some(value))
  }

}
