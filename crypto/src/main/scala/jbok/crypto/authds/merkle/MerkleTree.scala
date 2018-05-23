package jbok.crypto.authds.merkle

import jbok.crypto._
import jbok.crypto.authds.merkle.Node._
import jbok.crypto.hashing.Hashing
import scodec.bits.ByteVector

import scala.annotation.tailrec

sealed abstract class Node(val digest: ByteVector)
object Node {
  case class InternalNode[H: Hashing](left: Node, right: Node) extends Node((left.digest ++ right.digest).digested)
  case class LeafNode(override val digest: ByteVector) extends Node(digest)
}

class MerkleTree[H: Hashing](root: InternalNode[H], leaf2idx: Map[ByteVector, Int]) {
  val rootHash = root.digest

  val length: Int = leaf2idx.size

  val lengthWithEmptyLeafs: Int = {
    def log2(x: Double): Double = math.log(x) / math.log(2)

    Math.max(math.pow(2, math.ceil(log2(length))).toInt, 2)
  }

  def provideProof(hash: ByteVector): Option[MerkleProof[H]] = {
    leaf2idx.get(hash).flatMap(i => proofByIndex(i))
  }

  private def proofByIndex(index: Int): Option[MerkleProof[H]] =
    if (index >= 0 && index < length) {
      def loop(
          node: Node,
          i: Int,
          curLength: Int,
          acc: List[(ByteVector, Boolean)]): Option[(LeafNode, List[(ByteVector, Boolean)])] = {
        node match {
          case n: InternalNode[H] if i < curLength / 2 =>
            loop(n.left, i, curLength / 2, acc :+ (n.right.digest, true))
          case n: InternalNode[H] if i < curLength =>
            loop(n.right, i - curLength / 2, curLength / 2, acc :+ (n.left.digest, false))
          case n: LeafNode =>
            Some((n, acc.reverse))
          case _ =>
            None
        }
      }

      val leafWithProofs = loop(root, index, lengthWithEmptyLeafs, Nil)
      leafWithProofs.map { case (leaf, path) => MerkleProof(leaf.digest, path) }
    } else {
      None
    }
}

object MerkleTree {
  def apply[H: Hashing](data: List[ByteVector]): MerkleTree[H] = {
    val leafs = data.map(d => LeafNode(d))
    val leaf2idx: Map[ByteVector, Int] = leafs.zipWithIndex.map { case (leaf, i) =>
      leaf.digest -> i
    }.toMap
    val root = calcRoot(leafs)
    new MerkleTree(root, leaf2idx)
  }

  @tailrec
  def calcRoot[H: Hashing](nodes: List[Node]): InternalNode[H] = {
    val nextNodes = nodes
      .grouped(2)
      .map {
        case List(l, r) => InternalNode(l, r)
        case List(l) => InternalNode(l, l)
      }
      .toList

    if (nextNodes.length == 1) nextNodes.head else calcRoot(nextNodes)
  }
}
