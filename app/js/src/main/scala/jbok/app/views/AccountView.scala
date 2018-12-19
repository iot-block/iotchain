package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.AppState
import jbok.app.views.Nav.Tab
import jbok.core.models.{Account, Address, SignedTransaction}
import org.scalajs.dom._

case class AccountView(state: AppState) {
  val address: Var[Address] = Var(Address(0))
  val account: Var[Account] = Var(Account())

  val stxs: Vars[SignedTransaction] = Vars.empty


  @binding.dom
  val overview: Binding[Element] =
    <div>
      <table class="table-view">
        {
          val (address, account): (Address, Account) = state.selectedAccount.bind match {
            case Some((a, b, _)) => (a, b)
            case _ => (Address.empty, Account.empty())
          }
        <tr>
          <th>address</th>
          <td>{address.toString}</td>
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
        </tr>}
      </table>
    </div>

  @binding.dom
  val txsView: Binding[Element] =
    <div>
      {
        val stxs: List[SignedTransaction] = state.selectedAccount.bind match {
          case Some((_, _, a)) => a
          case _ => List.empty[SignedTransaction]
          }
        StxsView.render(stxs, state.hrefHandler).bind
      }
    </div>

  val tabView = new TabsView(
    Tab("Overview", Var(overview), ""),
    Tab("Transactions", Var(txsView), "")
  )

  @binding.dom
  def render: Binding[Element] =
    <div>
      <button id="account-back"  class="btn-back" onclick={state.hrefHandler}>back</button>
      {tabView.render.bind}
    </div>

}
