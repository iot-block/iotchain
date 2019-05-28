package jbok.crypto.authds.mpt

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import cats.effect.concurrent.Ref
import jbok.codec.HexPrefix
import jbok.codec.HexPrefix.Nibbles
import jbok.codec.rlp.implicits.RlpCodec
import jbok.persistent._
import scodec.bits.ByteVector
import jbok.crypto.authds.mpt.MptNode._
import MerklePatriciaTrie._
import jbok.codec.rlp.implicits._
import fs2._
import jbok.crypto._

final case class MerklePatriciaTrie[F[_], K: RlpCodec, V: RlpCodec](cf: ColumnFamily, store: KVStore[F], rootHash: Ref[F, Option[ByteVector]])(implicit F: Sync[F])
    extends SingleColumnKVStore[F, K, V] {
  override def put(key: K, value: V): F[Unit] =
    for {
      k       <- key.asBytes.pure[F]
      v       <- value.asBytes.pure[F]
      hashOpt <- rootHash.get
      nibbles = HexPrefix.bytesToNibbles(k)
      _ <- hashOpt match {
        case Some(hash) if hash != MerklePatriciaTrie.emptyRootHash =>
          for {
            root        <- getRootOpt.flatMap(opt => F.fromOption(opt, DBErr.NotFound))
            newRootHash <- putNode(root, nibbles, v) >>= commitPut
            _           <- rootHash.set(Some(newRootHash))
          } yield ()

        case _ =>
          val newRoot = LeafNode(nibbles, v)
          commitPut(NodeInsertResult(newRoot, newRoot :: Nil)).flatMap(newRootHash => rootHash.set(Some(newRootHash)))
      }
    } yield ()

  override def get(key: K): F[Option[V]] =
    (for {
      root <- OptionT(getRootOpt)
      nibbles = HexPrefix.bytesToNibbles(key.asBytes)
      v     <- OptionT(getNodeValue(root, nibbles))
      value <- OptionT.liftF(F.fromEither(v.asEither[V]))
    } yield value).value

  override def del(key: K): F[Unit] =
    for {
      hashOpt <- rootHash.get
      _ <- hashOpt match {
        case Some(hash) if hash != MerklePatriciaTrie.emptyRootHash =>
          val nibbles = HexPrefix.bytesToNibbles(key.asBytes)
          for {
            root        <- getRootOpt.flatMap(opt => F.fromOption(opt, DBErr.NotFound))
            newRootHash <- delNode(root, nibbles) >>= commitDel
            _           <- rootHash.set(Some(newRootHash))
          } yield ()

        case _ => F.unit
      }
    } yield ()

  override def writeBatch(puts: List[(K, V)], dels: List[K]): F[Unit] =
    dels.traverse(del) >> puts.traverse { case (k, v) => put(k, v) }.void

  override def writeBatch(ops: List[(K, Option[V])]): F[Unit] = {
    val (puts, dels) = ops.partition(_._2.isDefined)
    writeBatch(puts.collect { case (key, Some(value)) => key -> value }, dels.collect { case (key, None) => key })
  }

  override def toStream: Stream[F, (K, V)] =
    Stream.eval(toList).flatMap(Stream.emits)

  override def toList: F[List[(K, V)]] =
    toMap.map(_.toList)

  override def toMap: F[Map[K, V]] = {
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
        } yield value.fold(m)(v => m + ("" -> v))
    }

    for {
      root <- getRootOpt
      m    <- toMap0(root)
      res  <- m.map { case (k, v) => ByteVector.fromValidHex(k) -> v }.toList.traverse(decodeTuple)
    } yield res.toMap
  }

  override def size: F[Int] = {
    def size0(node: Option[MptNode]): F[Int] = node match {
      case None                          => 0.pure[F]
      case Some(LeafNode(_, _))          => 1.pure[F]
      case Some(ExtensionNode(_, child)) => getNodeByEntry(child) >>= size0
      case Some(bn @ BranchNode(_, value)) =>
        for {
          bn <- bn.activated.traverse { case (_, e) => getNodeByBranch(e) >>= size0 }.map(_.sum)
        } yield bn + (if (value.isEmpty) 0 else 1)
    }
    getRootOpt >>= size0
  }

  override def encodeTuple(kv: (K, V)): (ByteVector, ByteVector) =
    (kv._1.asBytes, kv._2.asBytes)

  override def decodeTuple(kv: (ByteVector, ByteVector)): F[(K, V)] =
    for {
      key   <- F.fromEither(kv._1.asEither[K])
      value <- F.fromEither(kv._2.asEither[V])
    } yield key -> value

  private[jbok] def getNodes: F[Map[String, MptNode]] = {
    def getNodes0(prefix: String, node: Option[MptNode]): F[Map[String, MptNode]] = node match {
      case None =>
        F.pure(Map.empty)

      case Some(leaf @ LeafNode(_, _)) =>
        F.pure(Map(prefix -> leaf))

      case Some(ext @ ExtensionNode(key, entry)) =>
        for {
          decoded <- getNodeByEntry(entry)
          subMap  <- getNodes0(prefix ++ key, decoded)
        } yield subMap + (prefix -> ext)

      case Some(bn @ BranchNode(_, _)) =>
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
      v <- store.get(cf, nodeHash)
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

  private def commit(newRoot: Option[MptNode], toPut: List[MptNode]): F[ByteVector] = {
    val newRootHash  = newRoot.map(_.hash).getOrElse(MerklePatriciaTrie.emptyRootHash)
    val newRootBytes = newRoot.map(_.capped).getOrElse(ByteVector.empty)

    val putOps = toPut
      .withFilter { node =>
        node.entry.isLeft || node.capped == newRootBytes
      }
      .map(x => x.hash -> x.bytes)

    store.writeBatch(cf, putOps, Nil).map(_ => newRootHash)
  }

  private def commitPut(nodeInsertResult: NodeInsertResult): F[ByteVector] =
    commit(Some(nodeInsertResult.newNode), nodeInsertResult.toPut)

  private def commitDel(nodeRemoveResult: NodeRemoveResult): F[ByteVector] =
    nodeRemoveResult match {
      case NodeRemoveResult(true, newRoot, _, toPut) =>
        commit(newRoot, toPut)

      case NodeRemoveResult(false, _, _, _) =>
        getRootHash
    }

  private def longestCommonPrefix(a: Nibbles, b: Nibbles): Int =
    a.zip(b).takeWhile(t => t._1 == t._2).length

  private def getNodeValue(node: MptNode, nibbles: Nibbles): F[Option[ByteVector]] =
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
          child <- getNodeByEntry(extNode.child).flatMap(opt => F.fromOption(opt, DBErr.NotFound))
          r     <- putNode(child, key.drop(l), value)
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
          branch <- getNodeByBranch(branchNode.branchAt(key.head)).flatMap(opt => F.fromOption(opt, DBErr.NotFound))
          r      <- putNode(branch, key.tail, value)
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
          next <- getNodeByEntry(node.child).flatMap(opt => F.fromOption(opt, DBErr.NotFound))
          r    <- delNode(next, key.drop(l))
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
      fix(node.copy(value = None), Nil).map(fixedNode => NodeRemoveResult(hasChanged = true, newNode = Some(fixedNode), toDel = node :: Nil, toPut = fixedNode :: Nil))

    // 3. otherwise
    case (branchNode, false) =>
      // try to remove 1 of the 16 branches
      for {
        child <- getNodeByBranch(branchNode.branchAt(key.head))
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
          branches(index) match {
            case Some(entry) =>
              val temporalExtNode = ExtensionNode(MerklePatriciaTrie.alphabet(index), entry)
              fix(temporalExtNode, notStoredYet)
            case None =>
              F.raiseError(new Exception(s"unexpected empty branches at ${index}"))
          }
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
            case None =>
              getNodeByEntry(extensionNode.child)
                .flatMap(opt => F.fromOption(opt, DBErr.NotFound)) // We search for the node in the db
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
  def apply[F[_]: Sync, K: RlpCodec, V: RlpCodec](cf: ColumnFamily, store: KVStore[F], root: Option[ByteVector] = None): F[MerklePatriciaTrie[F, K, V]] =
    for {
      ref <- Ref.of[F, Option[ByteVector]](root)
    } yield new MerklePatriciaTrie[F, K, V](cf, store, ref)

  def calcMerkleRoot[F[_]: Sync, V: RlpCodec](entities: List[V]): F[ByteVector] =
    for {
      db   <- MemoryKVStore[F]
      mpt  <- MerklePatriciaTrie[F, Int, V](ColumnFamily.default, db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put(k, v) }.sequence
      root <- mpt.getRootHash
    } yield root

  val emptyRootHash: ByteVector = ().asBytes.kec256

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
