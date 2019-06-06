package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Var
import jbok.app.AppState
import jbok.app.components.{TabPane, Tabs}
import jbok.core.api.HistoryTransaction
import jbok.core.models._
import org.scalajs.dom._
import scodec.bits.ByteVector

import scala.scalajs.js.Date

final case class BlockView(state: AppState, block: Block) {
  val stxs: Var[List[HistoryTransaction]] = Var(List.empty)
  val stxsView                            = StxsView(state, stxs)

  private def fetch() = {
    val nodeId = state.activeNode.value.getOrElse("")
    val client = state.clients.value.get(nodeId)
    client.foreach { jbokClient =>
      val p = for {
        txs <- jbokClient.account.getTransactionsByNumber(block.header.number.toInt)
        _ = stxs.value = txs
      } yield ()

      p.unsafeToFuture()
    }
  }

  fetch()

  @binding.dom
  val overview: Binding[Element] =
    <div>
      <table class="table-view"> {
      val header = block.header
      val size   = block.body.transactionList.size
      <tr>
          <th>Height:</th>
          <td>{header.number.toString}</td>
        </tr>
        <tr>
          <th>TimeStamp:</th>
          <td>{new Date(header.unixTimestamp).toDateString()}</td>
        </tr>
        <tr>
          <th>Transactions:</th>
          <td>{s"${size.toLong} transactions in this Block"}</td>
        </tr>
        <tr>
          <th>This Block Hash:</th>
          <td>{header.hash.toHex}</td>
        </tr>
        <tr>
          <th>Parent Hash:</th>
          <td><a onclick={(e: Event) => state.searchBlockHash(header.parentHash.toHex)}>{header.parentHash.toHex}</a></td>
        </tr>
        <tr>
          <th>Mined By:</th>
          <td>{header.beneficiary.toHex}</td>
        </tr>
        <tr>
          <th>Difficulty:</th>
          <td>{header.difficulty.toString}</td>
        </tr>
        <tr>
          <th>Gas Used:</th>
          <td>{header.gasUsed.toString}</td>
        </tr>
        <tr>
          <th>Gas Limit:</th>
          <td>{header.gasLimit.toString}</td>
        </tr>
        <tr>
          <th>Extra Data:</th>
          <td>{header.extra.bytes.toHex}</td>
        </tr>
    }
      </table>
    </div>

  @binding.dom
  val txsView: Binding[Element] =
    <div>
      {stxsView.render.bind}
    </div>

  val tabView = Tabs(
    List(TabPane("Overview", overview), TabPane("Transactions", txsView)),
    className = "tab-small"
  )

  @binding.dom
  def render: Binding[Element] =
    <div>
      {tabView.render.bind}
    </div>
}
