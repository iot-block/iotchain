package jbok.app

import com.thoughtworks.binding.Binding.{Var, _}
import com.thoughtworks.binding._
import jbok.app.components.{TabList, TabPane, Tabs}
import jbok.app.views._
import org.scalajs.dom._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@JSImport("css/normalize.css", JSImport.Namespace)
@js.native
object NormalizeCss extends js.Object

@JSImport("css/app.css", JSImport.Namespace)
@js.native
object AppCss extends js.Object

object JbokApp {
  val normalizeCss = NormalizeCss
  val appCss       = AppCss

  val config = AppConfig.default
  val state  = AppState(Var(config))
  state.init()

  val statusView       = StatusView(state).render
  val accountsView     = AccountsView(state).render
  val blocksView       = BlocksView(state).render
  val transactionsView = TxsView(state).render
  val contractView     = ContractView(state).render
  val configView       = ConfigView(state).render
  val searchView       = SearchView(state).render

  val accountsTab = TabPane("Accounts", accountsView, Some("fa-user-circle"))
  val blocksTab   = TabPane("Blocks", blocksView, Some("fa-th-large"))
  val txsTab      = TabPane("Transactions", transactionsView, Some("fa-arrow-circle-right"))
  val contractTab = TabPane("Contract", contractView, Some("fa-file-contract"))
  val configTab   = TabPane("", configView, Some("fa-cogs"))
  val searchTab   = TabPane("Search", searchView, Some("fa-search"))
  val tabs: Vars[TabPane] = Vars(
    accountsTab,
    blocksTab,
    txsTab,
    contractTab,
    searchTab,
    configTab
  )

  val tabList   = TabList(tabs, Var(tabs.value.head))
  val searchBar = SearchBar(state, onPressEnter = (e: Event) => tabList.selected.value = searchTab).render
  state.activeSearchView(() => tabList.selected.value = searchTab)

  @dom val right: Binding[Node] =
    <div class="nav-right">
      <div class="tab searchbar">{searchBar.bind}</div>
    </div>

  val navBar = Tabs.renderTabBar(tabList, Some(right), onchange = (e: Event) => state.clearSearch())

  @dom def render: Binding[BindingSeq[Node]] =
    <header>
      {navBar.bind}
      {statusView.bind}
    </header>
    <main>
    {Tabs.renderTabContent(tabList).bind}
    </main>
    <footer>
      {Copyright.render.bind}
    </footer>

  def main(args: Array[String]): Unit =
    dom.render(document.body, render)
}
