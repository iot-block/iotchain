package jbok.app.views

import com.thoughtworks.binding
import jbok.core.models.{Address, SignedTransaction, UInt256}
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Constants
import jbok.app.AppState
import jbok.app.helper.{ContractAddress, TableRenderHelper}
import org.scalajs.dom.Element
import org.scalajs.dom.Event

final case class StxsView(state: AppState, stxs: List[SignedTransaction]) {
  val header: List[String] = List("Tx Hash", "From", "To", "Value")
  val tableRenderHelper    = TableRenderHelper(header)

  @binding.dom
  def renderTable: Binding[Element] =
    <table class="table-view">
      {tableRenderHelper.renderTableHeader.bind}
      <tbody>
      {
        for (tx <- Constants(stxs: _*)) yield {
          <tr>
            <td>
              <a onclick={(e: Event) => state.searchTxHash(tx.hash.toHex)}>
                {tx.hash.toHex}
              </a>
            </td>
            <td>
              {
              val senderAddress = tx.senderAddress.getOrElse("error address").toString
              <a onclick={(e: Event) => state.searchAccount(senderAddress)}>
                {tx.senderAddress.getOrElse("error address").toString}
              </a>
              }
            </td>
            <td>
            {
              if (tx.receivingAddress == Address.empty) {
                val contractAddress = tx.senderAddress.map(ContractAddress.getContractAddress(_, UInt256(tx.nonce))).getOrElse("error address").toString
                <p>Contract: <a onclick={(e: Event) => state.searchAccount(contractAddress)}>{contractAddress}</a> Created</p>
              } else {
                <a onclick={(e: Event) => state.searchAccount(tx.receivingAddress.toString)}>{tx.receivingAddress.toString}</a>
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
      if(stxs.nonEmpty) {
        renderTable.bind
      } else {
        tableRenderHelper.renderEmptyTable.bind
      }
    }
    </div>
}
