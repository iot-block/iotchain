package jbok.app.service

import cats.effect.Sync
import cats.implicits._
import jbok.core.config.HistoryConfig
import jbok.core.ledger.History
import jbok.core.models.{Account, Address, Block}
import jbok.core.api.BlockTag

final class ServiceHelper[F[_]](config: HistoryConfig, history: History[F])(implicit F: Sync[F]) {
  def resolveBlock(tag: BlockTag): F[Option[Block]] = {
    def getBlock(number: BigInt): F[Option[Block]] =
      history.getBlockByNumber(number)

    tag match {
      case Left(number) if number >= 0 => getBlock(number)
      case BlockTag.latest             => history.getBestBlockNumber >>= getBlock
      case _                           => F.pure(None)
    }
  }

  def resolveAccount(address: Address, tag: BlockTag): F[Account] =
    for {
      blockOpt <- resolveBlock(tag)
      account <- blockOpt match {
        case Some(block) =>
          history
            .getAccount(address, block.header.number)
            .map(_.getOrElse(Account.empty(config.accountStartNonce)))
        case None =>
          F.pure(Account.empty(config.accountStartNonce))
      }
    } yield account

}
