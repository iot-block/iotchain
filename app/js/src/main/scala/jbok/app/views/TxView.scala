package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import jbok.core.models.SignedTransaction
import org.scalajs.dom.Element

object TxView {
  @binding.dom
  def render(tx: SignedTransaction): Binding[Element] = {
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
            <td>{SignedTransaction.getSender(tx).get.toString}</td>
          </tr>

          <tr>
            <th>to</th>
            <td>{tx.receivingAddress.toString}</td>
          </tr>

          <tr>
            <th>send data</th>
            <td>{tx.payload.toHex}</td>
          </tr>
        </tbody>
      </table>
    </div>
  }
}
