package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import jbok.app.AppState
import jbok.core.models.SignedTransaction
import org.scalajs.dom.{Element, Event}

object TxView {
  @binding.dom
  def render(state: AppState, tx: SignedTransaction): Binding[Element] =
    <div>
      <h3>{s"Transaction (${tx.hash.toHex})"}</h3>

      <table class="table-view">
        <tbody>
          <tr>
            <th>amount</th>
            <td>{tx.value.toString()}</td>
          </tr>

          <tr>
            <th>from</th>
            <td>
              {
                val senderAddress = tx.senderAddress.getOrElse("error address").toString
                <a onclick={(e: Event) => state.searchAccount(senderAddress)}>{senderAddress}</a>
              }
            </td>
          </tr>

          <tr>
            <th>to</th>
            <td><a onclick={(e: Event) => state.searchAccount(tx.receivingAddress.toString)}>{tx.receivingAddress.toString}</a></td>
          </tr>

          <tr>
            <th>send data</th>
            <td>{tx.payload.toHex}</td>
          </tr>
        </tbody>
      </table>
    </div>
}
