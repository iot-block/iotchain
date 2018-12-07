package jbok.crypto.authds.mpt

import cats.data.OptionT
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.codec.HexPrefix
import jbok.codec.HexPrefix.Nibbles
import jbok.codec.rlp.implicits._
import jbok.crypto._
import jbok.crypto.authds.mpt.MptNode._
import jbok.persistent.KeyValueDB
import scodec.Codec
import scodec.bits.ByteVector

final class MerklePatriciaTrie[F[_]](
    val namespace: ByteVector,
    val db: KeyValueDB[F],
    val rootHash: Ref[F, Option[ByteVector]]
)(implicit F: Sync[F])
    extends KeyValueDB[F] {
  private[this] val log = org.log4s.getLogger("MerklePatriciaTrie")

  import MerklePatriciaTrie._

  override protected[jbok] def getRaw(key: ByteVector): F[Option[ByteVector]] =
    (for {
      root <- OptionT(getRootOpt)
      nibbles = HexPrefix.bytesToNibbles(key)
      v <- OptionT(getNodeValue(root, nibbles))
    } yield v).value

  override protected[jbok] def putRaw(key: ByteVector, newVal: ByteVector): F[Unit] =
    for {
      hashOpt <- rootHash.get
      nibbles = HexPrefix.bytesToNibbles(key)
      _ <- hashOpt match {
        case Some(hash) if hash != MerklePatriciaTrie.emptyRootHash =>
          for {
            root        <- getRootOpt
            newRootHash <- putNode(root.get, nibbles, newVal) >>= commitPut
            _           <- rootHash.set(Some(newRootHash))
          } yield ()

        case _ =>
          val newRoot = LeafNode(nibbles, newVal)
          commitPut(NodeInsertResult(newRoot, newRoot :: Nil)).flatMap(newRootHash => rootHash.set(Some(newRootHash)))
      }
    } yield ()

  override protected[jbok] def delRaw(key: ByteVector): F[Unit] =
    for {
      hashOpt <- rootHash.get
      _ <- hashOpt match {
        case Some(hash) if hash != MerklePatriciaTrie.emptyRootHash =>
          val nibbles = HexPrefix.bytesToNibbles(key)
          for {
            root        <- getRootOpt
            newRootHash <- delNode(root.get, nibbles) >>= commitDel
            _           <- rootHash.set(Some(newRootHash))
          } yield ()

        case _ => F.unit
      }
    } yield ()

  override protected[jbok] def hasRaw(key: ByteVector): F[Boolean] =
    getRaw(key).map(_.isDefined)

  override protected[jbok] def keysRaw: F[List[ByteVector]] =
    toMapRaw.map(_.keys.toList)

  override protected[jbok] def size: F[Int] = {
    def size0(node: Option[MptNode]): F[Int] = node match {
      case None                          => 0.pure[F]
      case Some(LeafNode(_, _))          => 1.pure[F]
      case Some(ExtensionNode(_, child)) => getNodeByEntry(child) >>= size0
      case Some(bn @ BranchNode(_, value)) =>
        for {
          bn <- bn.activated.traverse { case (i, e) => getNodeByBranch(e) >>= size0 }.map(_.sum)
        } yield bn + (if (value.isEmpty) 0 else 1)
    }

    getRootOpt >>= size0
  }

  override protected[jbok] def toMapRaw: F[Map[ByteVector, ByteVector]] = {
    def toMap0(node: Option[MptNode]): F[Map[String, ByteVector]] = node match {
      case None =>
        F.pure(Map.empty)
      case Some(LeafNode(key, value)) =>
        F.pure(Map(key -> value))
      case Some(ExtensionNode(key, entry)) =>
        for {
          decoded <- getNodeByEntry(entry)
          subMap  <- toMap0(decoded)
        } yield subMap.map { case (k, v) => key ++ k -> v }
      case Some(bn @ BranchNode(_, value)) =>
        for {
          xs <- bn.activated.traverse { case (i, e) => (getNodeByBranch(e) >>= toMap0).map(_ -> i) }
          m = xs.foldLeft(Map.empty[String, ByteVector]) {
            case (acc, (cur, i)) => acc ++ cur.map { case (k, v) => MerklePatriciaTrie.alphabet(i) ++ k -> v }
          }
        } yield if (value.isDefined) m + ("" -> value.get) else m
    }

    for {
      root <- getRootOpt
      m    <- toMap0(root)
    } yield m.map { case (k, v) => ByteVector.fromValidHex(k) -> v }
  }

  override def keys[Key: Codec](namespace: ByteVector): F[List[Key]] =
    keysRaw.flatMap(_.traverse(k => decode[Key](k, namespace)))

  override def toMap[Key: Codec, Val: Codec](namespace: ByteVector): F[Map[Key, Val]] =
    for {
      mapRaw <- toMapRaw
      xs     <- mapRaw.toList.traverse { case (k, v) => (decode[Key](k, namespace), decode[Val](v)).tupled }
    } yield xs.toMap

  override protected[jbok] def writeBatchRaw(put: List[(ByteVector, ByteVector)], del: List[ByteVector]): F[Unit] =
    del.traverse(delRaw) >> put.traverse { case (k, v) => putRaw(k, v) }.void

  // note: since merkle trie only use key as tree path, we do not need
  // prefix key by namespace. we only need prefix node bytes hash when
  // we read or write the underlying db
  override def encode[A: Codec](a: A, prefix: ByteVector): F[ByteVector] =
    F.delay(Codec[A].encode(a).require.bytes)

  override def decode[A: Codec](bytes: ByteVector, prefix: ByteVector): F[A] =
    F.delay(Codec[A].decode(bytes.bits).require.value)

  ////////////////////////
  ////////////////////////

  private[jbok] def getNodes: F[Map[String, MptNode]] = {
    def getNodes0(prefix: String, node: Option[MptNode]): F[Map[String, MptNode]] = node match {
      case None =>
        F.pure(Map.empty)

      case Some(leaf @ LeafNode(key, value)) =>
        F.pure(Map(prefix -> leaf))

      case Some(ext @ ExtensionNode(key, entry)) =>
        for {
          decoded <- getNodeByEntry(entry)
          subMap  <- getNodes0(prefix ++ key, decoded)
        } yield subMap + (prefix -> ext)

      case Some(bn @ BranchNode(_, value)) =>
        for {
          xs <- bn.activated.traverse {
            case (i, e) => getNodeByBranch(e).flatMap(node => getNodes0(prefix + MerklePatriciaTrie.alphabet(i), node))
          }
          m = xs.foldLeft(Map.empty[String, MptNode]) {
            case (acc, cur) => acc ++ cur
          }
        } yield m + (prefix -> bn)
    }

    for {
      root <- getRootOpt
      m    <- getNodes0("", root)
    } yield m
  }

  private[jbok] def getRootHash: F[ByteVector] = rootHash.get.map(_.getOrElse(MerklePatriciaTrie.emptyRootHash))

  private[jbok] def getNodeByHash(nodeHash: ByteVector): F[Option[MptNode]] =
    for {
      v <- db.getRaw(namespace ++ nodeHash)
      node = v.map(x => nodeCodec.decode(x.bits).require.value)
    } yield node

  private[jbok] def getRootOpt: F[Option[MptNode]] = getRootHash >>= getNodeByHash

  private[jbok] def getNodeByEntry(entry: NodeEntry): F[Option[MptNode]] = entry match {
    case Left(hash)  => getNodeByHash(hash)
    case Right(node) => F.pure(Some(node))
  }

  private[jbok] def getNodeByBranch(branch: Option[NodeEntry]): F[Option[MptNode]] = branch match {
    case None    => F.pure(None)
    case Some(e) => getNodeByEntry(e)
  }

  private[jbok] def getNodeByKey(node: MptNode, key: ByteVector): F[Option[MptNode]] =
    getNodeByNibbles(node, HexPrefix.bytesToNibbles(key))

  private[jbok] def getNodeByNibbles(node: MptNode, nibbles: Nibbles): F[Option[MptNode]] =
    node match {
      case leaf @ LeafNode(k, _) => F.pure(if (k == nibbles) Some(leaf) else None)

      case ext: ExtensionNode =>
        val (commonKey, remainingKey) = nibbles.splitAt(ext.key.length)
        if (nibbles.length >= ext.key.length && (ext.key == commonKey)) {
          val node = for {
            child <- OptionT(getNodeByEntry(ext.child))
            node  <- OptionT(getNodeByNibbles(child, remainingKey))
          } yield node
          node.value
        } else {
          F.pure(None)
        }

      case branch: BranchNode =>
        if (nibbles.isEmpty) {
          F.pure(Some(branch))
        } else {
          val v = for {
            branch <- OptionT(getNodeByBranch(branch.branchAt(nibbles.head)))
            v      <- OptionT(getNodeByNibbles(branch, nibbles.tail))
          } yield v
          v.value
        }
    }

  private def commit(newRoot: Option[MptNode], toDel: List[MptNode], toPut: List[MptNode]): F[ByteVector] = {
    val newRootHash  = newRoot.map(_.hash).getOrElse(MerklePatriciaTrie.emptyRootHash)
    val newRootBytes = newRoot.map(_.capped).getOrElse(ByteVector.empty)

    getRootHash.flatMap { previousRootHash =>
      val delOps = toDel
        .withFilter { node =>
          node.entry.isLeft || node.hash == previousRootHash
        }
        .map(x => namespace ++ x.hash)

      val putOps = toPut
        .withFilter { node =>
          node.entry.isLeft || node.capped == newRootBytes
        }
        .map(x => namespace ++ x.hash -> x.bytes)

      db.writeBatchRaw(putOps, delOps).map(_ => newRootHash)
    }
  }

  private def commitPut(nodeInsertResult: NodeInsertResult): F[ByteVector] =
    commit(Some(nodeInsertResult.newNode), Nil, nodeInsertResult.toPut)

  private def commitDel(nodeRemoveResult: NodeRemoveResult): F[ByteVector] =
    nodeRemoveResult match {
      case NodeRemoveResult(true, newRoot, toDel, toPut) =>
        commit(newRoot, toDel, toPut)

      case NodeRemoveResult(false, _, _, _) =>
        getRootHash
    }

  private def longestCommonPrefix(a: Nibbles, b: Nibbles): Int =
    a.zip(b).takeWhile(t => t._1 == t._2).length

  private def getNodeValue(node: MptNode, nibbles: Nibbles): F[Option[ByteVector]] = {
    log.trace(s"""get nibbles "${nibbles}" in $node""")
    node match {
      case LeafNode(k, v) => F.pure(if (k == nibbles) Some(v) else None)

      case ext: ExtensionNode =>
        val (commonKey, remainingKey) = nibbles.splitAt(ext.key.length)
        if (nibbles.length >= ext.key.length && (ext.key == commonKey)) {
          val v = for {
            node <- OptionT(getNodeByEntry(ext.child))
            v    <- OptionT(getNodeValue(node, remainingKey))
          } yield v
          v.value
        } else {
          F.pure(None)
        }

      case branch: BranchNode =>
        if (nibbles.isEmpty) {
          branch.value.pure[F]
        } else {
          val v = for {
            branch <- OptionT(getNodeByBranch(branch.branchAt(nibbles.head)))
            v      <- OptionT(getNodeValue(branch, nibbles.tail))
          } yield v
          v.value
        }
    }
  }

  private def putNode(node: MptNode, key: Nibbles, value: ByteVector): F[NodeInsertResult] = node match {
    case leafNode: LeafNode     => putLeafNode(leafNode, key, value)
    case extNode: ExtensionNode => putExtensionNode(extNode, key, value)
    case branchNode: BranchNode => putBranchNode(branchNode, key, value)
  }

  private def putLeafNode(leafNode: LeafNode, key: Nibbles, value: ByteVector): F[NodeInsertResult] =
    longestCommonPrefix(leafNode.key, key) match {
      case l if l == leafNode.key.length && l == key.length =>
        // same keys, update value
        val newNode = leafNode.copy(value = value)
        NodeInsertResult(
          newNode,
          newNode :: Nil
        ).pure[F]

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
            toPut = maybeNewLeaf.toList ++ r.toPut
          )
        }

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
            toPut = maybeNewExtNode.toList ++ r.toPut
          )
        }

      case l if l == extNode.key.length =>
        // extension node's key is a prefix of the one being inserted
        // recursively insert on the child

        for {
          child <- getNodeByEntry(extNode.child)
          r     <- putNode(child.get, key.drop(l), value)
        } yield {
          val newExtNode = ExtensionNode(extNode.key, r.newNode.entry)
          NodeInsertResult(
            newNode = newExtNode,
            toPut = newExtNode +: r.toPut
          )
        }

      case l =>
        // Partially shared prefix, replace the node with an extension with the shared prefix
        // and the child as a new branch node
        val (sharedKeyPrefix, sharedKeySuffix) = extNode.key.splitAt(l)
        val tempExtNode                        = ExtensionNode(sharedKeySuffix, extNode.child)
        putNode(tempExtNode, key.drop(l), value).map { r =>
          val newExtNode = ExtensionNode(sharedKeyPrefix, r.newNode.entry)
          NodeInsertResult(
            newNode = newExtNode,
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
        toPut = newBranchNode :: Nil
      ).pure[F]
    } else {
      // Non empty key, insert the value into one branch
      if (branchNode.branchAt(key.head).isDefined) {
        // The associated branch is not empty, we recursively insert in that child
        for {
          branch <- getNodeByBranch(branchNode.branchAt(key.head))
          r      <- putNode(branch.get, key.tail, value)
        } yield {
          val newBranchNode = branchNode.updateBranch(key.head, Some(r.newNode.entry))
          NodeInsertResult(
            newNode = newBranchNode,
            toPut = newBranchNode +: r.toPut
          )
        }
      } else {
        // The associated child is empty, just insert with a leaf node
        val newLeafNode   = LeafNode(key.tail, value)
        val newBranchNode = branchNode.updateBranch(key.head, Some(newLeafNode.entry))
        NodeInsertResult(
          newNode = newBranchNode,
          toPut = newLeafNode :: newBranchNode :: Nil
        ).pure[F]
      }
    }

  private def delNode(node: MptNode, key: Nibbles): F[NodeRemoveResult] = node match {
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
          _ = log.trace(s"del next: ${next.get}")
          r <- delNode(next.get, key.drop(l))
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
                    new Exception("A trie with newRoot extension should have at least 2 values stored")
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
    case (BranchNode(_, Some(_)), true) =>
      // We need to remove old node and fix it because we removed the value
      fix(node.copy(value = None), Nil).map(fixedNode =>
        NodeRemoveResult(hasChanged = true, newNode = Some(fixedNode), toDel = node :: Nil, toPut = fixedNode :: Nil))

    // 3. otherwise
    case (branchNode, false) =>
      // try to remove 1 of the 16 branches
      for {
        child <- getNodeByBranch(branchNode.branchAt(key.head))
        _ = log.trace(s"should be here, ${child}")
        result <- child match {
          case Some(n) =>
            // recursively delete
            delNode(n, key.tail).flatMap {
              // branch changes, need to fix
              case NodeRemoveResult(true, newNodeOpt, toDel, toPut) =>
                val nodeToFix = branchNode.updateBranch(key.head, newNodeOpt.map(_.entry))

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

          case None =>
            // child not found in this branch node, so key is not present
            NodeRemoveResult(hasChanged = false, newNode = None).pure[F]
        }
      } yield result
  }

  private def fix(node: MptNode, notStoredYet: List[MptNode]): F[MptNode] = node match {
    case bn @ BranchNode(branches, value) =>
      val activeIndexes = bn.activated.map(_._1)

      (activeIndexes, value) match {
        case (Nil, None) => F.raiseError(new Exception("EmptyBranch"))
        case (index :: Nil, None) =>
          val temporalExtNode = ExtensionNode(MerklePatriciaTrie.alphabet(index), branches(index).get)
          fix(temporalExtNode, notStoredYet)
        case (Nil, Some(v)) => F.pure(LeafNode("", v))
        case _              => node.pure[F]
      }

    case extensionNode @ ExtensionNode(sharedKey, entry) =>
      val nextNode: F[MptNode] = entry match {
        case Left(nextHash) =>
          // If the node is not in the extension node then it might be a node to be inserted at the end of this remove
          // so we search in this list too
          notStoredYet.find(n => n.hash == nextHash) match {
            case Some(n) => F.pure(n)
            case None    => getNodeByEntry(extensionNode.child).map(_.get) // We search for the node in the db
          }

        case Right(nextNodeOnExt) => F.pure(nextNodeOnExt)
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
}

object MerklePatriciaTrie {
  def apply[F[_]: Sync](namespace: ByteVector,
                        db: KeyValueDB[F],
                        root: Option[ByteVector] = None): F[MerklePatriciaTrie[F]] =
    for {
      rootHash <- Ref.of[F, Option[ByteVector]](root)
    } yield new MerklePatriciaTrie[F](namespace, db, rootHash)

  def calcMerkleRoot[F[_]: Sync, V: Codec](entities: List[V]): F[ByteVector] =
    for {
      db   <- KeyValueDB.inmem[F]
      mpt  <- MerklePatriciaTrie[F](ByteVector.empty, db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put[Int, V](k, v, ByteVector.empty) }.sequence
      root <- mpt.getRootHash
    } yield root

  val emptyRootHash: ByteVector = rempty.encode(()).require.bytes.kec256

  val alphabet: Vector[String] = "0123456789abcdef".map(_.toString).toVector

  final case class NodeInsertResult(
      newNode: MptNode,
      toPut: List[MptNode]
  )

  final case class NodeRemoveResult(
      hasChanged: Boolean,
      newNode: Option[MptNode],
      toDel: List[MptNode] = Nil,
      toPut: List[MptNode] = Nil
  )

  object NodeRemoveResult {
    val noChanges: NodeRemoveResult = NodeRemoveResult(hasChanged = false, None)
  }
}
