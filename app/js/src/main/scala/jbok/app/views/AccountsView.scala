package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Vars}
import jbok.app.JbokClient
import jbok.app.components.{Form, FormEntry, Modal}
import jbok.core.models.{Account, Address}
import org.scalajs.dom._

class AccountsView(client: JbokClient) {

  val accounts = Vars[(Address, Account)]()
  accounts.value += Address(0x42) -> Account()
  accounts.value += Address(0x43) -> Account()
  accounts.value += Address(0x44) -> Account()

  val form = Form(Constants(FormEntry("password", "password")), { data =>
    client.admin.newAccount(data("password")).unsafeToFuture()
  })
  val modal = Modal("new account", form.render())

  @binding.dom
  def render(accounts: Vars[(Address, Account)] = accounts): Binding[Element] = {
    <div>
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
          {for ((address, account) <- accounts) yield {
          <tr>
            <td>
              <a>{address.toString}</a>
            </td>
            <td>
              {account.balance.toString}
            </td>
            <td>
              {account.nonce.toString}
            </td>
            <td>
              {account.storageRoot.toHex}
            </td>
            <td>
              {account.codeHash.toHex}
            </td>
          </tr>
        }}
        </tbody>
      </table>

      {modal.render().bind}
    </div>
  }
}
