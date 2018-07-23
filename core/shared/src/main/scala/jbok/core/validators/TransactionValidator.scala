package jbok.core.validators

import cats.data.EitherT
import cats.effect.Sync
import jbok.core.models.{Account, BlockHeader, SignedTransaction, UInt256}

sealed trait SignedTransactionInvalid extends Invalid
object SignedTransactionInvalid {
  case object TransactionSignatureInvalid extends SignedTransactionInvalid
  case class TransactionSyntaxInvalid(reason: String) extends SignedTransactionInvalid
  case class TransactionNonceInvalid(txNonce: UInt256, senderNonce: UInt256) extends SignedTransactionInvalid {
    override def toString: String =
      s"${getClass.getSimpleName}(Got tx nonce $txNonce but sender in mpt is: $senderNonce)"
  }
  case class TransactionNotEnoughGasForIntrinsicInvalid(txGasLimit: BigInt, txIntrinsicGas: BigInt)
      extends SignedTransactionInvalid {
    override def toString: String =
      s"${getClass.getSimpleName}(Tx gas limit ($txGasLimit) < tx intrinsic gas ($txIntrinsicGas))"
  }
  case class TransactionSenderCantPayUpfrontCostInvalid(upfrontCost: UInt256, senderBalance: UInt256)
      extends SignedTransactionInvalid {
    override def toString: String =
      s"${getClass.getSimpleName}(Upfrontcost ($upfrontCost) > sender balance ($senderBalance))"
  }
  case class TransactionGasLimitTooBigInvalid(txGasLimit: BigInt, accumGasUsed: BigInt, blockGasLimit: BigInt)
      extends SignedTransactionInvalid {
    override def toString: String =
      s"${getClass.getSimpleName}(Tx gas limit ($txGasLimit) + gas accum ($accumGasUsed) > block gas limit ($blockGasLimit))"
  }
}

class TransactionValidator[F[_]](implicit F: Sync[F]) {
  def validate(
      stx: SignedTransaction,
      senderAccount: Account,
      blockHeader: BlockHeader,
      upfrontGasCost: UInt256,
      accumGasUsed: BigInt
  ): EitherT[F, SignedTransactionInvalid, SignedTransaction] = EitherT.fromEither[F](Right(stx))
}
