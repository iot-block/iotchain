package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.views.Nav.{Tab, TabList}
import org.scalajs.dom.{Element, Event, Node}

class TabsView(tab: Tab*) {
  val tabs    = Vars(tab: _*)
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

  val navBar = Nav.render(left)

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
            {tab.content.value.bind}
          </div>
        }
        }
      </div>
    </div>
}
