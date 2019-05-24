package jbok.app.components

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import org.scalajs.dom._

final case class TabPane(name: String, content: Binding[Node], icon: Option[String] = None)
final case class TabList(tabPanes: Vars[TabPane], selected: Var[TabPane])

object Tabs {
  @binding.dom
  val renderEmpty: Binding[Element] = <div></div>

  @binding.dom
  def renderTitles(tabs: TabList,
                   className: String = "tab",
                   onchange: Event => Unit = (_: Event) => ()): Binding[Node] =
    <div class="nav-left">
    {
      for {
        tabPane <- tabs.tabPanes
      } yield {
        val isSelected = tabs.selected.bind == tabPane
        tabPane.icon match {
          case Some(icon) =>
            <div class={s"$className ${if (isSelected) "selected" else ""}"}
                 onclick={(e: Event) =>
                   tabs.selected.value = tabPane
                   onchange(e)
                 }>
              { <i class={s"fas fa-fw fa-lg $icon"}></i> }
              { tabPane.name }
            </div>
          case None =>
            <div class={s"$className ${if (isSelected) "selected" else ""}"}
                 onclick={(e: Event) =>
                   tabs.selected.value = tabPane
                   onchange(e)
                 }>
              { tabPane.name }
            </div>
        }
      }
    }
    </div>

  @binding.dom
  def renderTabBar(tabs: TabList,
                   extras: Option[Binding[Node]] = None,
                   className: String = "tab",
                   onchange: Event => Unit = (_: Event) => ()): Binding[Node] =
    <nav>
      {renderTitles(tabs, className, onchange).bind}
      {extras.getOrElse(renderEmpty).bind}
    </nav>

  @binding.dom
  def renderTabContent(tabs: TabList): Binding[Node] =
    <div>
      {
      for {
        tabPane <- tabs.tabPanes
      } yield {
        val isSelected = tabs.selected.bind == tabPane
        <div class={s"tab-content ${if (isSelected) "selected" else ""}"}>
          {tabPane.content.bind}
        </div>
      }
      }
    </div>
}

final case class Tabs(tabPanes: List[TabPane] = List.empty,
                      className: String = "tab",
                      extras: Option[Binding[Node]] = None,
                      onchange: Event => Unit = (_: Event) => ()) {
  val _tabPanes = Vars(tabPanes: _*)
  val _tabs     = TabList(_tabPanes, Var(_tabPanes.value.head))

  @binding.dom
  def render: Binding[Element] =
    <div>
      {Tabs.renderTabBar(_tabs, className=className, onchange=onchange).bind}
      {Tabs.renderTabContent(_tabs).bind}
    </div>
}
