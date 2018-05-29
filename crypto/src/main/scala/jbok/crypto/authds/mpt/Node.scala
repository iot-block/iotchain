package jbok.crypto.authds.mpt

import jbok.crypto.hashing.Hashing
import scodec.bits.ByteVector
import tsec.hashing.jca.SHA1

sealed trait Node {
  def isLeaf = false
  def isExtension = false
}

sealed trait KVNode extends Node {
  def key: String
}

object Node {
  type Hash = ByteVector
  type NodeEntry = Either[Hash, Node]

  object NodeEntry {
    def apply(node: Node): NodeEntry = {
      val encoded = NodeCodec.encode(node).require
      if (encoded.size < 32) Right(node)
      else Left(Hashing[SHA1].hash(encoded).digest)
    }
  }

  case object BlankNode extends Node

  case class LeafNode(key: String, value: ByteVector) extends KVNode {
    override def isLeaf: Boolean = true
  }

  case class ExtensionNode(key: String, entry: NodeEntry) extends KVNode {
    override def isExtension: Boolean = true
  }

  case class BranchNode(branches: List[NodeEntry], value: Option[ByteVector]) extends Node {
    def mapValue(v: ByteVector): BranchNode = {
      this.copy(value = Some(v))
    }

    def branchAt(char: Char): Either[ByteVector, Node] = {
      val idx = Integer.parseInt(char.toString, 16)
      branches(idx)
    }

    def updated(char: Char, entry: Either[ByteVector, Node]): BranchNode = {
      val idx = Integer.parseInt(char.toString, 16)
      this.copy(branches = this.branches.updated(idx, entry))
    }
  }

  object BranchNode {
    def char2idx(ch: Char): Int = {
      Integer.parseInt(ch.toString, 16)
    }

    def empty: BranchNode = apply(None)

    def apply(value: Option[ByteVector]): BranchNode = {
      val branches = List.fill[Either[ByteVector, Node]](16)(Right(BlankNode))
      BranchNode(branches, value)
    }

    def apply(branch: Char, entry: NodeEntry): BranchNode = {
      empty.updated(branch, entry)
    }

    def apply(branchKey: Char, entry: NodeEntry, value: ByteVector): BranchNode = {
      val node = BranchNode(Some(value))
      node.updated(branchKey, entry)
    }
  }

}
