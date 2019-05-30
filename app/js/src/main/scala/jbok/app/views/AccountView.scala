package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Var
import jbok.app.AppState
import jbok.app.components.{TabPane, Tabs}
import jbok.core.api.HistoryTransaction
import jbok.core.models.{Account, Address}
import org.scalajs.dom._

final case class AccountView(state: AppState, address: Address, account: Account) {
  val stxs: Var[List[HistoryTransaction]] = Var(List.empty)
  val stxsView                            = StxsView(state, stxs)

  private def fetch() = {
    val nodeId = state.activeNode.value.getOrElse("")
    val client = state.clients.value.get(nodeId)
    client.foreach { jbokClient =>
      val p = for {
        txs <- jbokClient.account.getTransactions(address, 1, 100)
        _ = stxs.value = txs
      } yield ()

      p.unsafeToFuture()
    }
  }

  fetch()

  @binding.dom
  val overview: Binding[Element] =
    <div>
      <table class="table-view">
        <tr>
          <th>address</th>
          <td><a onclick={(_: Event) => state.searchAccount(address.toString)}>{address.toString}</a></td>
        </tr>
        <tr>
          <th>nonce</th>
          <td>{account.nonce.toString}</td>
        </tr>
        <tr>
          <th>balance</th>
          <td>{account.balance.toString}</td>
        </tr>
        <tr>
          <th>storage</th>
          <td>{account.storageRoot.toString}</td>
        </tr>
        <tr>
          <th>code</th>
          <td>{account.codeHash.toString}</td>
        </tr>
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
