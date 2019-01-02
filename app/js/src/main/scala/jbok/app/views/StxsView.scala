package jbok.app.views

import com.thoughtworks.binding
import jbok.core.models.{Address, SignedTransaction, UInt256}
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Constants
import jbok.app.ContractAddress
import org.scalajs.dom.Element
import org.scalajs.dom.Event

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.EitherProjectionPartial"))
object StxsView {
  @binding.dom
  def render(stxs: List[SignedTransaction], hrefHandler: Event => Unit): Binding[Element] =
    <div>
      <table class="table-view">
        <thead>
          <tr>
            <th>Tx Hash</th>
            <th>From</th>
            <th>To</th>
            <th>Value</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {
            for (tx <- Constants(stxs: _*)) yield {
              <tr>
                <td>
                  <a onclick={hrefHandler} type="tx">
                    {tx.hash.toHex}
                  </a>
                </td>
                <td>
                  <a onclick={hrefHandler} type="address">
                    {tx.senderAddress.get.toString}
                  </a>
                </td>
                <td>
                  {
                    if (tx.receivingAddress == Address.empty) {
                      <p>Contract: <a onclick={hrefHandler} type="address">{ContractAddress.getContractAddress(tx.senderAddress.get, UInt256(tx.nonce)).toString}</a> Created</p>
                    } else {
                      <a onclick={hrefHandler} type="address">{tx.receivingAddress.toString}</a>
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
    </div>

}
