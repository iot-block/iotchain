package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.components.{Form2, Modal}
import jbok.app.{AppState, SimuClient}
import jbok.core.models.{Account, Address}
import org.scalajs.dom.{Element, _}
import scodec.bits.ByteVector

final case class AccountsView(state: AppState) {
  val newAccountForm = Form2(
    Constants(CustomInput("Password", "password", None, (addr: String) => true, "password")), { data =>
      if (data.values.forall(_.isValid))
        state.currentId.value.map { id =>
          val p = for {
            address <- state.clients.value(id).personal.newAccount(data("Password").value)
          } yield address
          p.unsafeToFuture()
        }
    }
  )
  def onConfirm(): Unit = {
    newAccountForm.submit(newAccountForm.entryMap)
    newAccountForm.clear()
  }
  def onCancel(): Unit =
    newAccountForm.clear()
  val newAccountModal = Modal("new account", newAccountForm.render(), () => onConfirm(), () => onCancel())

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        val accounts = state.currentId.bind match {
          case Some(id) => state.accounts.value.getOrElse(id, Var(Map.empty[Address, Var[Account]]))
          case _ => Var(Map.empty[Address, Var[Account]])
        }
        <table class="table-view">
        <thead>
          <tr>
            <th>Address</th>
            <th>Balance</th>
            <th>Nonce</th>
            <th>StorageRoot</th>
            <th>CodeHash</th>
          </tr>
        </thead>
        <tbody>
          {
            for ((address, account) <- Constants(accounts.bind.toList: _*)) yield {
              <tr>
                <td>
                  <a onclick={state.hrefHandler} type="address">
                    {address.toString}
                  </a>
                </td>
                <td>
                  {account.bind.balance.toString}
                </td>
                <td>
                  {account.bind.nonce.toString}
                </td>
                <td>
                  {account.bind.storageRoot.toHex}
                </td>
                <td>
                  {account.bind.codeHash.toHex}
                </td>
              </tr>
            }
          }
        </tbody>
        </table>
      }
      <div class="flex">
        {newAccountModal.render().bind}
      </div>
    </div>

}
