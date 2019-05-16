package jbok.core.api

import java.net.URI

import io.circe.Json
import jbok.network.rpc.RpcClient

trait JbokClient[F[_]] {
  def uri: URI
  def client: RpcClient[F, Json]
  def account: AccountAPI[F]
  def admin: AdminAPI[F]
  def block: BlockAPI[F]
  def contract: ContractAPI[F]
  def personal: PersonalAPI[F]
  def transaction: TransactionAPI[F]
}
