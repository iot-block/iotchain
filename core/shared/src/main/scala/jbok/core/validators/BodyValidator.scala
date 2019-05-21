package jbok.core.validators

import cats.effect.Sync
import cats.implicits._

import jbok.codec.rlp.implicits._
import jbok.core.models._
import jbok.core.validators.BodyInvalid._
import jbok.crypto.authds.mpt.MerklePatriciaTrie

object BodyInvalid {
  case object BodyTransactionsHashInvalid extends Exception("BlockTransactionsHashInvalid")
}

private[validators] object BodyValidator {

  /** validate whether the transactions do match their claimed root hashes */
  def validate[F[_]: Sync](block: Block): F[Unit] =
    for {
      _ <- validateTransactionRoot(block)
    } yield ()

  private def validateTransactionRoot[F[_]](block: Block)(implicit F: Sync[F]): F[Unit] =
    MerklePatriciaTrie.calcMerkleRoot[F, SignedTransaction](block.body.transactionList).flatMap { root =>
      if (root == block.header.transactionsRoot) F.unit
      else F.raiseError(BodyTransactionsHashInvalid)
    }
}
