package jbok.app

import java.net.URI

import com.thoughtworks.binding.Binding.{BindingSeq, Var, Vars}
import com.thoughtworks.binding.{Binding, FutureBinding, dom}
import jbok.app.components.{SelectItem, SelectMenu, Spinner}
import jbok.app.views.Nav.{Tab, TabList}
import jbok.app.views._
import jbok.network.execution._
import org.scalajs.dom._

object JbokApp {
  val uri    = new URI(s"ws://localhost:8888")
  val client = JbokClient(uri).unsafeToFuture().value.get.get

  val selectMenu =
    new SelectMenu("please select max").render(Vars(SelectItem("50", "50"), SelectItem("100", "100")))

  val statusView       = new StatusView(client).render()
  val accountsView     = new AccountsView(client).render()
  val blocksView       = new BlocksView(client).render()
  val transactionsView = TxsView.render()
  val simulationsView  = SimulationsView.render()
  val configView       = ConfigView.render()

  val tabs = Vars(
    Tab("Accounts", accountsView, "fa-user-circle"),
    Tab("Blocks", blocksView, "fa-th-large"),
    Tab("Transactions", transactionsView, "fa-arrow-circle-right"),
    Tab("Simulations", simulationsView, "fa-stethoscope"),
    Tab("", configView, "fa-cogs")
  )

  val tabList   = TabList(tabs, Var(tabs.value.head))
  val searchBar = new SearchBar(client).render()

  @dom val left: Binding[Node] =
    <div class="nav-left">
    {
      for {
        tab <- tabList.tabs
      } yield {
        val isSelected = tabList.selected.bind == tab
        <div class={s"tab ${if (isSelected) "selected" else ""}"}
             onclick={_: Event => tabList.selected.value = tab}>
          <i class={s"fas fa-fw fa-lg ${tab.icon}"}></i>
          { tab.name }
        </div>
      }
    }
    </div>

  @dom val right: Binding[Node] =
    <div class="nav-right">
      <div class="tab searchbar">{searchBar.bind}</div>
    </div>

  val navBar = Nav.render(left, right)

  val spinner = Spinner.render(FutureBinding {
    FutureUtil.delay(5000)
  })

  @dom def render: Binding[BindingSeq[Node]] =
    <header>
      {navBar.bind}
      {statusView.bind}
    </header>
    <main>
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

    {spinner.bind}
    </main>
    <footer>
      {Copyright.render.bind}
    </footer>

  def main(args: Array[String]): Unit =
    dom.render(document.body, render)
}
