package jbok.app.views

import cats.effect.IO
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Var
import jbok.app.components.Skeleton
import jbok.app.execution._
import jbok.app.{AppState, Search}
import jbok.common.log.Logger
import jbok.common.math.N
import jbok.core.models.{Account, Address, Block, SignedTransaction}
import org.scalajs.dom._
import scodec.bits.ByteVector

final case class SearchResult(state: AppState, search: Search) {
  private val log = Logger[IO]

  val block: Var[Option[Block]]           = Var(None)
  val account: Var[Option[Account]]       = Var(None)
  val stx: Var[Option[SignedTransaction]] = Var(None)
  val failed: Var[Boolean]                = Var(false)

  def fetch(): Unit = {
    val nodeId = state.activeNode.value.getOrElse("")
    val client = state.clients.value.get(nodeId)
    client.foreach { client =>
      search.searchType match {
        case "blockNumber" =>
          val p = for {
            blockOpt <- client.block.getBlockByNumber(N(search.keyword))
            _ = if (blockOpt.nonEmpty) {
              log.d(s"nonempty: ${search.keyword.toInt}, ${blockOpt}")
              block.value = blockOpt
            } else {
              failed.value = true
            }
          } yield ()
          p.timeout(state.config.value.clientTimeout)
            .handleErrorWith((_: Throwable) => IO.delay { failed.value = true })
            .unsafeToFuture()
        case "blockHash" =>
          val p = for {
            blockOpt <- client.block.getBlockByHash(ByteVector.fromHex(search.keyword).getOrElse(ByteVector.empty))
            _ = if (blockOpt.nonEmpty) {
              block.value = blockOpt
            } else {
              failed.value = true
            }
          } yield ()
          p.timeout(state.config.value.clientTimeout)
            .handleErrorWith(
              (e: Throwable) =>
                for {
                  _ <- IO.delay(e.printStackTrace())
                  _ <- IO.delay { failed.value = true }
                } yield ()
            )
            .unsafeToFuture()
        case "txHash" =>
          val p = for {
            txOpt <- client.transaction.getTx(ByteVector.fromHex(search.keyword).getOrElse(ByteVector.empty))
            _ = if (txOpt.nonEmpty) {
              stx.value = txOpt
            } else {
              failed.value = true
            }
          } yield ()
          p.timeout(state.config.value.clientTimeout)
            .handleErrorWith((e: Throwable) => IO.delay { failed.value = true })
            .unsafeToFuture()
        case "account" =>
          val p = for {
            a <- client.account.getAccount(Address(ByteVector.fromHex(search.keyword).getOrElse(ByteVector.empty)), search.blockParam)
            _ = account.value = Some(a)
          } yield ()
          p.timeout(state.config.value.clientTimeout)
            .handleErrorWith((e: Throwable) => IO.delay { failed.value = true })
            .unsafeToFuture()
      }
    }
  }

  fetch()

  @binding.dom
  def render: Binding[Element] =
    <div>
      <div class="search">
        <h2>Search Result:</h2>
      </div>
      {
      if (failed.bind) {
        <div>
            cannot find data.
          </div>
      } else {
        search.searchType match {
          case "blockNumber" | "blockHash" =>
            block.bind match {
              case Some(block) => BlockView(state, block).render.bind
              case None        => Skeleton.renderTable(3, 3).bind
            }
          case "txHash" =>
            stx.bind match {
              case Some(stx) => TxView.render(state, stx).bind
              case None      => Skeleton.renderTable(2, 5).bind
            }
          case "account" =>
            account.bind match {
              case Some(account) => AccountView(state, Address.fromHex(search.keyword), account).render.bind
              case None          => Skeleton.renderTable(2, 5).bind
            }
          case _ =>
            <div>
                404
              </div>
        }
      }
    }
    </div>
}
