package jbok.crypto.authds.mpt

import cats.Traverse
import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
import jbok.crypto.authds.mpt.Node._
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector
import jbok.codec.HexPrefix
import jbok.codec.HexPrefix.Nibbles

case class NodeInsertResult(
    newNode: Node,
    toDel: List[Node] = Nil,
    toPut: List[Node] = Nil
)

case class NodeRemoveResult(
    hasChanged: Boolean,
    newNode: Option[Node],
    toDel: List[Node] = Nil,
    toPut: List[Node] = Nil
)

object NodeRemoveResult {
  val noChanges: NodeRemoveResult = NodeRemoveResult(hasChanged = false, None)
}

sealed abstract class MPTException(message: String) extends RuntimeException(message)
case class MissingNodeException(hash: ByteVector, message: String) extends MPTException(message) {
  def this(hash: ByteVector) = this(hash, s"Node ${hash.toHex} not found")
}
case object EmptyBranchException extends MPTException("empty branch node")

object MPTrie {
  val emptyRootHash: ByteVector = BlankNode.hash

  val alphabet: Vector[String] = "0123456789abcdef".map(_.toString).toVector

  def inMemory[F[_]: Sync](root: ByteVector = emptyRootHash): F[MPTrie[F]] =
    for {
      db <- KeyValueDB.inMemory[F]
      rootHash <- fs2.async.refOf[F, ByteVector](root)
    } yield new MPTrie[F](db, rootHash)

  def apply[F[_]: Sync](db: KeyValueDB[F], root: ByteVector = emptyRootHash): F[MPTrie[F]] =
    for {
      rootHash <- fs2.async.refOf[F, ByteVector](root)
    } yield new MPTrie[F](db, rootHash)
}

class MPTrie[F[_]](db: KeyValueDB[F], rootHash: Ref[F, ByteVector])(implicit F: Sync[F]) extends KeyValueDB[F] {
  private[this] val log = org.log4s.getLogger

  override def get(key: ByteVector): F[ByteVector] =
    getOpt(key).map(_.get)

  override def getOpt(key: ByteVector): F[Option[ByteVector]] =
    for {
      root <- getRoot
      nibbles = HexPrefix.bytesToNibbles(key)
      v <- getNodeValue(root, nibbles)
    } yield v

  override def put(key: ByteVector, newVal: ByteVector): F[Unit] =
    for {
      root <- getRoot
      nibbles = HexPrefix.bytesToNibbles(key)
      newRootHash <- putNode(root, nibbles, newVal) >>= commitPut
      _ <- rootHash.setSync(newRootHash)
    } yield ()

  override def del(key: ByteVector): F[Unit] =
    for {
      root <- getRoot
      nibbles = HexPrefix.bytesToNibbles(key)
      newRootHash <- delNode(root, nibbles) >>= commitDel
      _ <- rootHash.setSync(newRootHash)
    } yield ()

  override def has(key: ByteVector): F[Boolean] = getOpt(key).map(_.isDefined)

  override def keys: F[List[ByteVector]] = toMap.map(_.keys.toList)

  override def toMap: F[Map[ByteVector, ByteVector]] = {
    def toMap0(node: Node): F[Map[String, ByteVector]] = node match {
      case BlankNode => F.pure(Map.empty)
      case LeafNode(key, value) =>
        F.pure(Map(key -> value))
      case ExtensionNode(key, entry) =>
        for {
          decoded <- getNodeByEntry(entry)
          subMap <- toMap0(decoded)
        } yield subMap.map { case (k, v) => key ++ k -> v }
      case BranchNode(branches, value) =>
        for {
          xs <- branches.zipWithIndex.traverse { case (e, i) => (getNodeByEntry(e) >>= toMap0).map(_ -> i) }
          m = xs.foldLeft(Map.empty[String, ByteVector]) {
            case (acc, (cur, i)) => acc ++ cur.map { case (k, v) => MPTrie.alphabet(i) ++ k -> v }
          }
        } yield if (value.isDefined) m + ("" -> value.get) else m
    }

    for {
      root <- getRoot
      m <- toMap0(root)
    } yield m.map { case (k, v) => ByteVector.fromValidHex(k) -> v }
  }

  override def writeBatch[G[_]: Traverse](ops: G[(ByteVector, Option[ByteVector])]): F[Unit] =
    ops
      .map {
        case (key, Some(v)) => put(key, v)
        case (key, None)    => del(key)
      }
      .sequence
      .void

  override def clear(): F[Unit] = db.clear() *> rootHash.setSync(MPTrie.emptyRootHash)

  ////////////////////////
  ////////////////////////


  def getRootHash: F[ByteVector] = rootHash.get

  def getNodeByHash(nodeHash: ByteVector): F[Node] =
    db.getOpt(nodeHash).map(_.map(x => NodeCodec.decode(x).require).getOrElse(BlankNode))

  def getNodeByEntry(entry: NodeEntry): F[Node] = entry match {
    case Left(hash) if hash == MPTrie.emptyRootHash => F.pure(BlankNode)
    case Left(hash)                                 => getNodeByHash(hash)
    case Right(node)                                => node.pure[F]
  }

  def getRoot: F[Node] =
    for {
      hash <- getRootHash
      root <- getNodeByHash(hash)
    } yield root

  def getNodeByKey(node: Node, key: ByteVector): F[Option[Node]] =
    getNodeByNibbles(node, HexPrefix.bytesToNibbles(key))

  def getNodeByNibbles(node: Node, nibbles: Nibbles): F[Option[Node]] = {
    log.debug(s"""get nibbles "${nibbles}" in $node""")
    node match {
      case BlankNode => F.pure(None)

      case leaf @ LeafNode(k, _) => F.pure(if (k == nibbles) Some(leaf) else None)

      case ext: ExtensionNode =>
        val (commonKey, remainingKey) = nibbles.splitAt(ext.key.length)
        if (nibbles.length >= ext.key.length && (ext.key == commonKey)) {
          getNodeByEntry(ext.child).flatMap(child => getNodeByNibbles(child, remainingKey))
        } else {
          F.pure(None)
        }

      case branch: BranchNode =>
        if (nibbles.isEmpty) {
          F.pure(Some(branch))
        } else {
          for {
            branch <- getNodeByEntry(branch.branchAt(nibbles.head))
            v <- getNodeByNibbles(branch, nibbles.tail)
          } yield v
        }
    }
  }

  def getNodes: F[List[(ByteVector, Node)]] =
    for {
      keys <- db.keys
      values <- keys.traverse(db.get).map(_.map(x => NodeCodec.decode(x).require))
    } yield keys.zip(values)

  private def commit(newRoot: Option[Node], toDel: List[Node], toPut: List[Node]): F[ByteVector] = {
    val newRootHash = newRoot.map(_.hash).getOrElse(MPTrie.emptyRootHash)
    val newRootBytes = newRoot.map(_.capped).getOrElse(ByteVector.empty)

    rootHash.get.flatMap { previousRootHash =>
      val delOps = toDel
        .filter { node =>
          node.entry.isLeft || node.hash == previousRootHash
        }
        .map(x => { log.debug(s"del $x"); x })
        .map(x => x.hash -> None)

      val putOps = toPut
        .filter { node =>
          node.entry.isLeft || node.capped == newRootBytes
        }
        .map(x => { log.debug(s"put$x"); x })
        .map(x => x.hash -> Some(x.bytes))

      val batch = delOps ++ putOps
      db.writeBatch(batch).map(_ => newRootHash)
    }
  }

  private def commitPut(nodeInsertResult: NodeInsertResult): F[ByteVector] =
    commit(Some(nodeInsertResult.newNode), nodeInsertResult.toDel, nodeInsertResult.toPut)

  private def commitDel(nodeRemoveResult: NodeRemoveResult): F[ByteVector] =
    nodeRemoveResult match {
      case NodeRemoveResult(true, newRoot, toDel, toPut) =>
        commit(newRoot, toDel, toPut)

      case NodeRemoveResult(false, _, _, _) =>
        rootHash.get
    }

  private def longestCommonPrefix(a: Nibbles, b: Nibbles): Int =
    a.zip(b).takeWhile(t => t._1 == t._2).length

  private def getNodeValue(node: Node, nibbles: Nibbles): F[Option[ByteVector]] = {
    log.debug(s"""get nibbles "${nibbles}" in $node""")
    node match {
      case BlankNode => F.pure(None)

      case LeafNode(k, v) => F.pure(if (k == nibbles) Some(v) else None)

      case ext: ExtensionNode =>
        val (commonKey, remainingKey) = nibbles.splitAt(ext.key.length)
        if (nibbles.length >= ext.key.length && (ext.key == commonKey)) {
          getNodeByEntry(ext.child).flatMap(child => getNodeValue(child, remainingKey))
        } else {
          F.pure(None)
        }

      case branch: BranchNode =>
        if (nibbles.isEmpty) {
          branch.value.pure[F]
        } else {
          for {
            branch <- getNodeByEntry(branch.branchAt(nibbles.head))
            v <- getNodeValue(branch, nibbles.tail)
          } yield v
        }
    }
  }

  private def putNode(node: Node, key: Nibbles, value: ByteVector): F[NodeInsertResult] = node match {
    case BlankNode =>
      val newRoot = LeafNode(key, value)
      log.debug(s"put ${key} in blank")
      NodeInsertResult(newRoot, Nil, newRoot :: Nil).pure[F]
    case leafNode: LeafNode =>
      log.debug(s"put ${key} in ${leafNode}")
      putLeafNode(leafNode, key, value)
    case extNode: ExtensionNode =>
      log.debug(s"put ${key} in ${extNode}")
      putExtensionNode(extNode, key, value)
    case branchNode: BranchNode =>
      log.debug(s"put ${key} in ${branchNode}")
      putBranchNode(branchNode, key, value)
  }

  private def putLeafNode(leafNode: LeafNode, key: Nibbles, value: ByteVector): F[NodeInsertResult] =
    longestCommonPrefix(leafNode.key, key) match {
      case 0 =>
        // split to a branch node
        val (branchNode, maybeNewLeaf) =
          if (leafNode.key.isEmpty) {
            // current node has no key, branch node with only value
            BranchNode.withOnlyValue(leafNode.value) -> None
          } else {
            // create branch node with one branch
            val newLeafNode = LeafNode(leafNode.key.tail, leafNode.value)
            BranchNode.withSingleBranch(leafNode.key.head, newLeafNode.entry, None) -> Some(newLeafNode)
          }

        putNode(branchNode, key, value).map { r =>
          NodeInsertResult(
            newNode = r.newNode,
            toDel = leafNode +: r.toDel.filterNot(_ == branchNode),
            toPut = maybeNewLeaf.toList ++ r.toPut
          )
        }

      case l if l == leafNode.key.length && l == key.length =>
        // same keys, update value
        val newNode = leafNode.copy(value = value)
        NodeInsertResult(
          newNode,
          leafNode :: Nil,
          newNode :: Nil
        ).pure[F]

      case l =>
        // partially matched prefix, replace this leaf node with an extension and a branch node
        val (prefix, suffix) = key.splitAt(l)
        val branchNode =
          if (l == leafNode.key.length) BranchNode.withOnlyValue(leafNode.value)
          else LeafNode(leafNode.key.drop(l), leafNode.value)

        putNode(branchNode, suffix, value).map { r =>
          val newExtNode = ExtensionNode(prefix, r.newNode.entry)
          NodeInsertResult(
            newNode = newExtNode,
            toDel = leafNode +: r.toDel.filterNot(_ == branchNode),
            toPut = newExtNode +: r.toPut
          )
        }
    }

  private def putExtensionNode(extNode: ExtensionNode, key: Nibbles, value: ByteVector): F[NodeInsertResult] =
    longestCommonPrefix(extNode.key, key) match {
      case 0 =>
        // split
        val (branchNode, maybeNewExtNode) = {
          if (extNode.key.length == 1) {
            BranchNode.withSingleBranch(extNode.key.head, extNode.child, None) -> None
          } else {
            // The new branch node will have an extension that replaces current one
            val newExtNode = ExtensionNode(extNode.key.tail, extNode.child)
            BranchNode.withSingleBranch(extNode.key.head, newExtNode.entry, None) -> Some(newExtNode)
          }
        }

        putNode(branchNode, key, value).map { r =>
          NodeInsertResult(
            newNode = r.newNode,
            toDel = extNode +: r.toDel.filterNot(_ == branchNode),
            toPut = maybeNewExtNode.toList ++ r.toPut
          )
        }

      case l if l == extNode.key.length =>
        // extension node's key is a prefix of the one being inserted
        // recursively insert on the child

        for {
          child <- getNodeByEntry(extNode.child)
          r <- putNode(child, key.drop(l), value)
        } yield {
          val newExtNode = ExtensionNode(extNode.key, r.newNode.entry)
          NodeInsertResult(
            newNode = newExtNode,
            toDel = extNode +: r.toDel,
            toPut = newExtNode +: r.toPut
          )
        }

      case l =>
        // Partially shared prefix, replace the node with an extension with the shared prefix
        // and the child as a new branch node
        val (sharedKeyPrefix, sharedKeySuffix) = extNode.key.splitAt(l)
        val tempExtNode = ExtensionNode(sharedKeySuffix, extNode.child)
        putNode(tempExtNode, key.drop(l), value).map { r =>
          val newExtNode = ExtensionNode(sharedKeyPrefix, r.newNode.entry)
          NodeInsertResult(
            newNode = newExtNode,
            toDel = extNode +: r.toDel.filterNot(_ == tempExtNode),
            toPut = newExtNode +: r.toPut
          )
        }
    }

  private def putBranchNode(branchNode: BranchNode, key: Nibbles, value: ByteVector): F[NodeInsertResult] =
    if (key.isEmpty) {
      // the key is empty, update the branch node value
      val newBranchNode = branchNode.copy(value = Some(value))
      NodeInsertResult(
        newNode = newBranchNode,
        toDel = branchNode :: Nil,
        toPut = newBranchNode :: Nil
      ).pure[F]
    } else {
      // Non empty key, insert the value into one branch
      if (branchNode.branchAt(key.head) != Right(BlankNode)) {
        // The associated branch is not empty, we recursively insert in that child
        for {
          branch <- getNodeByEntry(branchNode.branchAt(key.head))
          r <- putNode(branch, key.tail, value)
        } yield {
          val newBranchNode = branchNode.updateBranch(key.head, r.newNode.entry)
          NodeInsertResult(
            newNode = newBranchNode,
            toDel = branchNode +: r.toDel,
            toPut = newBranchNode +: r.toPut
          )
        }
      } else {
        // The associated child is empty, just insert with a leaf node
        val newLeafNode = LeafNode(key.tail, value)
        val newBranchNode = branchNode.updateBranch(key.head, newLeafNode.entry)
        NodeInsertResult(
          newNode = newBranchNode,
          toDel = branchNode :: Nil,
          toPut = newLeafNode :: newBranchNode :: Nil
        ).pure[F]
      }
    }

  private def delNode(node: Node, key: Nibbles): F[NodeRemoveResult] = node match {
    case BlankNode                    => NodeRemoveResult.noChanges.pure[F]
    case leafNode: LeafNode           => delLeafNode(leafNode, key)
    case extensionNode: ExtensionNode => delExtensionNode(extensionNode, key)
    case branchNode: BranchNode       => delBranchNode(branchNode, key)
  }

  private def delLeafNode(node: LeafNode, key: Nibbles): F[NodeRemoveResult] =
    if (node.key == key) {
      // We found the node to delete
      NodeRemoveResult(
        hasChanged = true,
        newNode = None,
        toDel = node :: Nil
      ).pure[F]
    } else {
      NodeRemoveResult(hasChanged = false, newNode = None).pure[F]
    }

  private def delExtensionNode(node: ExtensionNode, key: Nibbles): F[NodeRemoveResult] =
    longestCommonPrefix(node.key, key) match {
      case l if l == node.key.length =>
        // recursively delete the child
        for {
          next <- getNodeByEntry(node.child)
          r <- delNode(next, key.drop(l))
          result <- r match {
            case NodeRemoveResult(true, newNodeOpt, toDel, toPut) =>
              // If we changed the child, we need to fix this extension node
              newNodeOpt match {
                case Some(newNode) =>
                  val toFix = ExtensionNode(node.key, newNode.entry)
                  fix(toFix, toPut).map { fixedNode =>
                    NodeRemoveResult(
                      hasChanged = true,
                      newNode = Some(fixedNode),
                      toDel = node +: toDel,
                      toPut = fixedNode +: toPut
                    )
                  }

                case None =>
                  F.raiseError[NodeRemoveResult](
                    new RuntimeException("A trie with newRoot extension should have at least 2 values stored")
                  )
              }

            case r @ NodeRemoveResult(false, _, _, _) =>
              r.copy(newNode = None).pure[F]
          }
        } yield result

      case _ =>
        NodeRemoveResult(hasChanged = false, newNode = Some(node)).pure[F]
    }

  private def delBranchNode(node: BranchNode, key: Nibbles): F[NodeRemoveResult] = (node, key.isEmpty) match {
    // 1. the key matches but the value isEmpty, ignore anyway
    case (BranchNode(_, None), true) => NodeRemoveResult.noChanges.pure[F]

    // 2. the key matches and value isDefined, delete the value
    case (BranchNode(branches, Some(_)), true) =>
      // We need to remove old node and fix it because we removed the value
      fix(BranchNode(branches, None), Nil).map(fixedNode =>
        NodeRemoveResult(hasChanged = true, newNode = Some(fixedNode), toDel = node :: Nil, toPut = fixedNode :: Nil))

    // 3. otherwise
    case (branchNode @ BranchNode(branches, value), false) =>
      // try to remove 1 of the 16 branches
      for {
        child <- getNodeByEntry(branchNode.branchAt(key.head))
        result <- child match {
          case BlankNode =>
            // key not present, ignore anyway
            NodeRemoveResult.noChanges.pure[F]

          case n =>
            // recursively delete
            delNode(n, key.tail).flatMap {
              // branch changes, need to fix
              case NodeRemoveResult(true, newNodeOpt, toDel, toPut) =>
                val nodeToFix = newNodeOpt match {
                  case Some(newNode) => branchNode.updateBranch(key.head, newNode.entry)
                  case None          => BranchNode(branches.updated(key.head, BlankNode.entry), value)
                }

                fix(nodeToFix, toPut).map { fixedNode =>
                  NodeRemoveResult(
                    hasChanged = true,
                    newNode = Some(fixedNode),
                    toDel = node +: toDel,
                    toPut = fixedNode +: toPut
                  )
                }

              // no removal on branches
              case r @ NodeRemoveResult(false, _, _, _) =>
                r.copy(newNode = None).pure[F]
            }
        }
      } yield result
  }

  private def fix(node: Node, notStoredYet: List[Node]): F[Node] = node match {
    case BranchNode(branches, value) =>
      val usedIndexes = branches.indices.toList
        .filter(i => branches(i) != Right(BlankNode))

      (usedIndexes, value) match {
        case (Nil, None) => F.raiseError(EmptyBranchException)
        case (index :: Nil, None) =>
          val temporalExtNode = ExtensionNode(MPTrie.alphabet(index), branches(index))
          fix(temporalExtNode, notStoredYet)
        case (Nil, Some(v)) => F.pure(LeafNode("", v))
        case _              => node.pure[F]
      }

    case extensionNode @ ExtensionNode(sharedKey, entry) =>
      val nextNode = entry match {
        case Left(nextHash) =>
          // If the node is not in the extension node then it might be a node to be inserted at the end of this remove
          // so we search in this list too
          notStoredYet.find(n => n.hash == nextHash) match {
            case Some(n) => n.pure[F]
            case None    => getNodeByEntry(extensionNode.child) // We search for the node in the db
          }

        case Right(nextNodeOnExt) => nextNodeOnExt.pure[F]
      }

      nextNode.map {
        // Compact Two extensions into one
        case ExtensionNode(subSharedKey, subNext) => ExtensionNode(sharedKey ++ subSharedKey, subNext)
        // Compact the extension and the leaf into the same leaf node
        case LeafNode(subRemainingKey, subValue) => LeafNode(sharedKey ++ subRemainingKey, subValue)
        // It's ok
        case _: BranchNode => node
      }

    case _ => node.pure[F]
  }

  def size: F[Int] = {
    def size0(node: Node): F[Int] = node match {
      case BlankNode               => 0.pure[F]
      case LeafNode(_, _)          => 1.pure[F]
      case ExtensionNode(_, child) => getNodeByEntry(child) >>= size0
      case BranchNode(branches, value) =>
        for {
          bn <- branches.traverse(x => getNodeByEntry(x) >>= size0).map(_.sum)
        } yield bn + (if (value.isEmpty) 0 else 1)
    }

    getRoot >>= size0
  }
}
