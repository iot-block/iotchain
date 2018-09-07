package jbok.core.validators

import cats.effect.Sync
import jbok.core.Configs.BlockChainConfig
import jbok.core.models._
import jbok.core.validators.TransactionInvalid._
import jbok.evm.EvmConfig
import cats.implicits._

object TransactionInvalid {
  case object TransactionSignatureInvalid             extends Exception("SignedTransactionInvalid")
  case class TransactionSyntaxInvalid(reason: String) extends Exception(s"TransactionSyntaxInvalid: ${reason}")
  case class TransactionNonceInvalid(txNonce: UInt256, senderNonce: UInt256)
      extends Exception(
        s"TransactionNonceInvalid(got tx nonce $txNonce but sender in mpt is: $senderNonce)"
      )
  case class TransactionNotEnoughGasForIntrinsicInvalid(txGasLimit: BigInt, txIntrinsicGas: BigInt)
      extends Exception(
        s"TransactionNotEnoughGasForIntrinsicInvalid(xx gas limit ($txGasLimit) < tx intrinsic gas ($txIntrinsicGas))"
      )
  case class TransactionSenderCantPayUpfrontCostInvalid(upfrontCost: UInt256, senderBalance: UInt256)
      extends Exception(
        s"TransactionSenderCantPayUpfrontCostInvalid(upfrontcost ($upfrontCost) > sender balance ($senderBalance))"
      )
  case class TransactionGasLimitTooBigInvalid(txGasLimit: BigInt, accumGasUsed: BigInt, blockGasLimit: BigInt)
      extends Exception(
        s"TransactionGasLimitTooBigInvalid(tx gas limit ($txGasLimit) + acc gas ($accumGasUsed) > block gas limit ($blockGasLimit))"
      )
}

class TransactionValidator[F[_]](blockChainConfig: BlockChainConfig)(implicit F: Sync[F]) {
  val secp256k1n: BigInt = BigInt("115792089237316195423570985008687907852837564279074904382605163141518161494337")

  /**
    * Validates if the transaction is syntactically valid (lengths of the transaction fields are correct)
    *
    * @param stx Transaction to validate
    * @return Either the validated transaction or TransactionSyntaxError if an error was detected
    */
  private def checkSyntacticValidity(stx: SignedTransaction): F[SignedTransaction] = {
    import stx._

    val maxNonceValue = BigInt(2).pow(8 * 32) - 1
    val maxGasValue   = BigInt(2).pow(8 * 32) - 1
    val maxValue      = BigInt(2).pow(8 * 32) - 1
    val maxR          = BigInt(2).pow(8 * 32) - 1
    val maxS          = BigInt(2).pow(8 * 32) - 1

    if (nonce > maxNonceValue)
      F.raiseError(TransactionSyntaxInvalid(s"Invalid nonce: $nonce > $maxNonceValue"))
    else if (gasLimit > maxGasValue)
      F.raiseError(TransactionSyntaxInvalid(s"Invalid gasLimit: $gasLimit > $maxGasValue"))
    else if (gasPrice > maxGasValue)
      F.raiseError(TransactionSyntaxInvalid(s"Invalid gasPrice: $gasPrice > $maxGasValue"))
    else if (value > maxValue)
      F.raiseError(TransactionSyntaxInvalid(s"Invalid value: $value > $maxValue"))
    else if (r > maxR)
      F.raiseError(TransactionSyntaxInvalid(s"Invalid signatureRandom: $r > $maxR"))
    else if (s > maxS)
      F.raiseError(TransactionSyntaxInvalid(s"Invalid signature: $s > $maxS"))
    else
      F.pure(stx)
  }

  /**
    * Validates if the transaction signature is valid as stated in appendix F in YP
    *
    * @param stx SignedTransaction to validate
    * @param blockNumber Number of the block for this transaction
    * @return Either the validated transaction or TransactionSignatureError if an error was detected
    */
  private def validateSignatureFormat(stx: SignedTransaction, blockNumber: BigInt): F[SignedTransaction] = {
    import stx._

    val beforeHomestead = blockNumber < blockChainConfig.homesteadBlockNumber
    val beforeEIP155    = blockNumber < blockChainConfig.eip155BlockNumber

    val validR = r > 0 && r < secp256k1n
    val validS = s > 0 && s < (if (beforeHomestead) secp256k1n else secp256k1n / 2)
//    val validSigningSchema = if (beforeEIP155) !stx.isChainSpecific else true
    val validSigningSchema = true

    if (validR && validS && validSigningSchema) F.pure(stx)
    else F.raiseError(TransactionSignatureInvalid)
  }

  /**
    * Validates if the transaction nonce matches current sender account's nonce
    *
    * @param nonce       Transaction.nonce to validate
    * @param senderNonce Nonce of the sender of the transaction
    * @return Either the validated transaction or a TransactionNonceError
    */
  private def validateNonce(nonce: BigInt, senderNonce: UInt256): F[BigInt] =
    if (senderNonce == UInt256(nonce)) F.pure(nonce)
    else F.raiseError(TransactionNonceInvalid(UInt256(nonce), senderNonce))

  /**
    * Validates the sender account balance contains at least the cost required in up-front payment.
    *
    * @param senderBalance Balance of the sender of the tx
    * @param upfrontCost Upfront cost of the transaction tx
    * @return Either the validated transaction or a TransactionSenderCantPayUpfrontCostError
    */
  private def validateAccountHasEnoughGasToPayUpfrontCost(senderBalance: UInt256, upfrontCost: UInt256): F[UInt256] =
    if (senderBalance >= upfrontCost) F.pure(senderBalance)
    else F.raiseError(TransactionSenderCantPayUpfrontCostInvalid(upfrontCost, senderBalance))

  /**
    * Validates the gas limit is no smaller than the intrinsic gas used by the transaction.
    *
    * @param stx Transaction to validate
    * @param blockHeaderNumber Number of the block where the stx transaction was included
    * @return Either the validated transaction or a TransactionNotEnoughGasForIntrinsicError
    */
  private def validateGasLimitEnoughForIntrinsicGas(stx: SignedTransaction,
                                                    blockHeaderNumber: BigInt): F[SignedTransaction] = {
    val config         = EvmConfig.forBlock(blockHeaderNumber, blockChainConfig)
    val txIntrinsicGas = config.calcTransactionIntrinsicGas(stx.payload, stx.isContractInit)
    if (stx.gasLimit >= txIntrinsicGas) F.pure(stx)
    else F.raiseError(TransactionNotEnoughGasForIntrinsicInvalid(stx.gasLimit, txIntrinsicGas))
  }

  /**
    * The sum of the transaction’s gas limit and the gas utilised in this block prior must be no greater than the
    * block’s gasLimit
    *
    * @param gasLimit      Transaction to validate
    * @param accGasUsed    Gas spent within tx container block prior executing stx
    * @param blockGasLimit Block gas limit
    */
  private def validateBlockHasEnoughGasLimitForTx(gasLimit: BigInt,
                                                  accGasUsed: BigInt,
                                                  blockGasLimit: BigInt): F[BigInt] =
    if (gasLimit + accGasUsed <= blockGasLimit) F.pure(gasLimit)
    else F.raiseError(TransactionGasLimitTooBigInvalid(gasLimit, accGasUsed, blockGasLimit))

  def validate(
      stx: SignedTransaction,
      senderAccount: Account,
      blockHeader: BlockHeader,
      upfrontGasCost: UInt256,
      accGasUsed: BigInt
  ): F[Unit] =
    for {
      _ <- checkSyntacticValidity(stx)
      _ <- validateSignatureFormat(stx, blockHeader.number)
      _ <- validateNonce(stx.nonce, senderAccount.nonce)
      _ <- validateGasLimitEnoughForIntrinsicGas(stx, blockHeader.number)
      _ <- validateAccountHasEnoughGasToPayUpfrontCost(senderAccount.balance, upfrontGasCost)
      _ <- validateBlockHasEnoughGasLimitForTx(stx.gasLimit, accGasUsed, blockHeader.gasLimit)
    } yield ()
}
