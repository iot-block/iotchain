package jbok.app.service

import cats.effect.Sync
import cats.implicits._
import jbok.common.math.N
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.models.{Address, Block, SignedTransaction, Transaction}
import jbok.core.api.{BlockTag, CallTx, ContractAPI}
import scodec.bits.ByteVector
import spire.syntax.all._
import spire.compat._

final class ContractService[F[_]](history: History[F], executor: BlockExecutor[F], helper: ServiceHelper[F])(implicit F: Sync[F]) extends ContractAPI[F] {
//  override def getABI(address: Address): F[Option[Ast.ContractDef]] = ???
//
//  override def getSourceCode(address: Address): F[Option[String]] = ???

  override def call(callTx: CallTx, tag: BlockTag): F[ByteVector] =
    for {
      (stx, block) <- doCall(callTx, tag)
      txResult     <- executor.simulateTransaction(stx, callTx.from.getOrElse(Address.empty), block.header)
    } yield txResult.vmReturnData

  override def getEstimatedGas(callTx: CallTx, tag: BlockTag): F[N] =
    for {
      (stx, block) <- doCall(callTx, tag)
      gas          <- executor.binarySearchGasEstimation(stx, callTx.from.getOrElse(Address.empty), block.header)
    } yield gas

  override def getGasPrice: F[N] = {
    val blockDifference = N(30)
    for {
      bestBlock <- history.getBestBlockNumber
      gasPrices <- List
        .range(bestBlock - blockDifference, bestBlock)
        .filter(_ >= N(0))
        .traverse(history.getBlockByNumber)
        .map(_.flatten.flatMap(_.body.transactionList).map(_.gasPrice))
      gasPrice = if (gasPrices.nonEmpty) {
        gasPrices.qsum / gasPrices.length
      } else {
        N(0)
      }
    } yield gasPrice
  }

  private def doCall[A](callTx: CallTx, tag: BlockTag): F[(SignedTransaction, Block)] =
    for {
      stx      <- prepareTransaction(callTx, tag)
      blockOpt <- helper.resolveBlock(tag)
      block    <- F.fromOption(blockOpt, new Exception())
    } yield (stx, block)

  private def prepareTransaction(callTx: CallTx, tag: BlockTag): F[SignedTransaction] =
    for {
      gasLimit <- getGasLimit(callTx, tag)
      tx = Transaction(0, callTx.gasPrice, gasLimit, callTx.to, callTx.value, callTx.data)
    } yield SignedTransaction(tx, 0.toByte, ByteVector(0), ByteVector(0))

  private def getGasLimit(callTx: CallTx, tag: BlockTag): F[N] =
    callTx.gas match {
      case Some(gas) => gas.pure[F]
      case None      => helper.resolveBlock(tag).map(_.map(_.header.gasLimit).getOrElse(N(0)))
    }
}
