package jbok.app.views

import com.thoughtworks.binding
import jbok.core.models.{Address, SignedTransaction, UInt256}
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var}
import jbok.app.AppState
import jbok.app.helper.{ContractAddress, TableRenderHelper}
import jbok.core.api.HistoryTransaction
import org.scalajs.dom.Element
import org.scalajs.dom.Event

final case class StxsView(state: AppState, stxs: Var[List[HistoryTransaction]]) {
  val header: List[String] = List("Tx Hash", "From", "To", "Value")
  val tableRenderHelper    = TableRenderHelper(header)

  @binding.dom
  def renderTable(stxs: List[HistoryTransaction]): Binding[Element] =
    <table class="table-view">
      {tableRenderHelper.renderTableHeader.bind}
      <tbody>
      {
        for (tx <- Constants(stxs: _*)) yield {
          <tr>
            <td>
              <a onclick={(e: Event) => state.searchTxHash(tx.txHash.toHex)}>
                {tx.txHash.toHex}
              </a>
            </td>
            <td>
              {
              val senderAddress = tx.fromAddress.toString
              <a onclick={(e: Event) => state.searchAccount(senderAddress)}>
                {senderAddress}
              </a>
              }
            </td>
            <td>
            {
              if (tx.toAddress== Address.empty) {
                val contractAddress = ContractAddress.getContractAddress(tx.fromAddress, UInt256(tx.nonce)).toString
                <p>Create Contract: <a onclick={(e: Event) => state.searchAccount(contractAddress)}>{contractAddress}</a></p>
              } else {
                <a onclick={(e: Event) => state.searchAccount(tx.toAddress.toString)}>{tx.toAddress.toString}</a>
              }
            }
            </td>
            <td>
              {tx.value.toString}
            </td>
          </tr>
        }
      }
      </tbody>
    </table>

  @binding.dom
  def render: Binding[Element] =
    <div>
    {
      val history = stxs.bind
      if(history.nonEmpty) {
        renderTable(history).bind
      } else {
        tableRenderHelper.renderEmptyTable.bind
      }
    }
    </div>
}
