package jbok.crypto.authds.merkle

import jbok.crypto._
import jbok.crypto.authds.merkle.Node.{InternalNode, LeafNode}
import scodec.bits.ByteVector

import scala.annotation.tailrec

sealed abstract class Node(val digest: ByteVector)
object Node {
  case class InternalNode(left: Node, right: Node) extends Node((left.digest ++ right.digest).kec256)
  case class LeafNode(override val digest: ByteVector) extends Node(digest)
}

class MerkleTree(root: InternalNode, leaf2idx: Map[ByteVector, Int]) {
  val rootHash = root.digest

  val length: Int = leaf2idx.size

  val lengthWithEmptyLeafs: Int = {
    def log2(x: Double): Double = math.log(x) / math.log(2)

    Math.max(math.pow(2, math.ceil(log2(length))).toInt, 2)
  }

  def provideProof(hash: ByteVector): Option[MerkleProof] = {
    leaf2idx.get(hash).flatMap(i => proofByIndex(i))
  }

  private def proofByIndex(index: Int): Option[MerkleProof] =
    if (index >= 0 && index < length) {
      def loop(
          node: Node,
          i: Int,
          curLength: Int,
          acc: List[(ByteVector, Boolean)]): Option[(LeafNode, List[(ByteVector, Boolean)])] = {
        node match {
          case n: InternalNode if i < curLength / 2 =>
            loop(n.left, i, curLength / 2, acc :+ (n.right.digest, true))
          case n: InternalNode if i < curLength =>
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
  def apply(data: List[ByteVector]): MerkleTree = {
    val leafs = data.map(d => LeafNode(d))
    val leaf2idx: Map[ByteVector, Int] = leafs.zipWithIndex.map { case (leaf, i) =>
      leaf.digest -> i
    }.toMap
    val root = calcRoot(leafs)
    new MerkleTree(root, leaf2idx)
  }

  @tailrec
  def calcRoot(nodes: List[Node]): InternalNode = {
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
