package jbok.app.txgen

import cats.effect._
import cats.implicits._
import fs2._
import jbok.core.config.FullConfig
import jbok.common.log.Logger
import jbok.core.api.JbokClient
import jbok.core.mining.TxGen
import jbok.core.models.{Account, Address, UInt256}
import jbok.crypto.signature.KeyPair

final class ValidTxGen[F[_]](config: FullConfig, client: JbokClient[F], keyPairs: List[KeyPair])(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  implicit val chainId = config.genesis.chainId

  val addresses: List[Address] = keyPairs.map(Address.apply)

  def getAccounts: F[List[Account]] =
    for {
      accounts <- addresses.traverse(client.account.getAccount(_))
      nonces   <- addresses.traverse(client.account.getEstimatedNonce)
      newAccounts = accounts.zip(nonces).map {
        case (account, nonce) => account.copy(nonce = UInt256(nonce))
      }
    } yield newAccounts

  def submitTransactions(nTx: Int = 10): F[Unit] =
    for {
      accounts  <- getAccounts
      (_, stxs) <- TxGen.genTxs[F](nTx, keyPairs.zip(accounts).toMap)
      _         <- stxs.traverse(stx => client.transaction.sendTx(stx))
      _         <- log.info(s"send ${nTx} transactions to network")
    } yield ()

  val stream: Stream[F, Unit] =
    Stream.eval(log.i(s"starting TxGen")) ++
      Stream
        .fixedDelay[F](config.mining.period)
        .evalMap(_ => submitTransactions())
}
