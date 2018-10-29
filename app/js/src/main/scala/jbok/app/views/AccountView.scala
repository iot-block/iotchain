package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.components.Modal
import jbok.app.views.Nav.{Tab, TabList}
import jbok.core.models.{Account, Address, SignedTransaction, Transaction}
import jbok.crypto.signature.{ECDSA, Signature}
import org.scalajs.dom._
import scodec.bits.ByteVector

case class AccountView() {
  val address: Var[Address] = Var(Address(0))
  val account: Var[Account] = Var(Account())

  val stxs: Vars[SignedTransaction] = Vars.empty

  def fetch() = {
    val p = for {
      keyPair <- Signature[ECDSA].generateKeyPair()
      tx = Transaction(0, 0, 0, None, BigInt(10000), ByteVector.empty)
      stx <- SignedTransaction.signIO(tx, keyPair)
      addr = Address(keyPair)
      _    = address.value = addr
      _    = stxs.value += stx
    } yield ()

    p.unsafeToFuture()
  }

  fetch()

  @binding.dom
  val overview: Binding[Element] =
    <div>
      <table class="table-view">
        <tr>
          <th>address</th>
          <td>{address.bind.toString}</td>
        </tr>
        <tr>
          <th>balance</th>
          <td>{account.bind.balance.toString}</td>
        </tr>
        <tr>
          <th>nonce</th>
          <td>{account.bind.nonce.toString}</td>
        </tr>
        <tr>
          <th>storage</th>
          <td>{account.bind.storageRoot.toString}</td>
        </tr>
        <tr>
          <th>code</th>
          <td>{account.bind.codeHash.toString}</td>
        </tr>
      </table>
    </div>

  @binding.dom
  val txsView: Binding[Element] =
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
          for (tx <- Constants(stxs.bind: _*)) yield {
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

  val tabs = Vars(
    Tab("Overview", overview, ""),
    Tab("Transactions", txsView, ""),
  )

  val tabList = TabList(tabs, Var(tabs.value.head))

  @binding.dom
  val left: Binding[Node] =
    <div class="nav-left">
      {
      for {
        tab <- tabList.tabs
      } yield {
        val isSelected = tabList.selected.bind == tab
        <div class={s"tab-small ${if (isSelected) "selected" else ""}"}
             onclick={_: Event => tabList.selected.value = tab}>
          { tab.name }
        </div>
      }
      }
    </div>

  @binding.dom
  val right: Binding[Node] =
    <div class="nav-right"></div>

  val navBar = Nav.render(left, right)

  @binding.dom
  def render: Binding[Element] =
    <div>
      {navBar.bind}
      <div>
      {
      for {
        tab <- tabList.tabs
      } yield {
        val isSelected = tabList.selected.bind == tab
        <div class={s"tab-content ${if (isSelected) "selected" else ""}"}>
          {tab.content.bind}
        </div>
      }
      }
      </div>
    </div>

}
