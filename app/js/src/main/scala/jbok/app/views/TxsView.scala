package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Vars
import jbok.app.components.Modal
import jbok.core.models._
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, Signature}
import org.scalajs.dom._
import scodec.bits.ByteVector

object TxsView {
  val transactions = Vars[SignedTransaction]()

  def fetch() = {
    val p = for {
      keyPair <- Signature[ECDSA].generateKeyPair()
      tx = Transaction(0, 0, 0, None, BigInt(10000), ByteVector.empty)
      stx <- SignedTransaction.signIO(tx, keyPair)
      _ = transactions.value += stx
    } yield ()

    p.unsafeToFuture()
  }

  fetch()

  @binding.dom
  def render(transactions: Vars[SignedTransaction] = transactions): Binding[Element] =
    <div>
      <table class="table-view">
        <thead>
          <tr>
            <th>Tx Hash</th>
            <th>From</th>
            <th>To</th>
            <th>Value</th>
            <th>Detail</th>
          </tr>
        </thead>
        <tbody>{
          for (tx <- transactions) yield {
          <tr>
            <td>
              <a>{tx.hash.toHex}</a>
            </td>
            <td>
              <a>{SignedTransaction.getSender(tx).get.toString}</a>
            </td>
            <td>
              <a>{tx.receivingAddress.toString}</a>
            </td>
            <td>
              {tx.value.toString}
            </td>
            <td>
              {Modal("view", TxView.render(tx)).render().bind}
            </td>
          </tr>
        }}
        </tbody>
      </table>
    </div>
}
