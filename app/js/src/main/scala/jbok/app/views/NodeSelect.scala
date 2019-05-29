package jbok.app.views

import jbok.app.AppState
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Constants
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLSelectElement

final case class NodeSelect(state: AppState) {
  @binding.dom
  def render: Binding[Element] = {
    val onChangeHandler = { event: Event =>
      event.currentTarget match {
        case select: HTMLSelectElement =>
          val v = select.options(select.selectedIndex).value
          if (v == "default") {
            state.clearSelectedNode()
          } else {
            state.selectNode(state.nodes.value.get(v).map(_.id))
          }
        case _ =>
      }
    }
    {
      <select id="nodeSelect" onchange={onChangeHandler}> {
        val selected = state.activeNode.bind
        val nodes= state.nodes.bind
        for {
          node <- Constants(nodes.toList.map(_._2).sortBy(_.port): _*)
          isSelected = if (selected.contains(node.id)) true else false
        } yield {
          <option value={node.id} selected={isSelected}>
            {node.addr.toString} 
          </option>
        }}
        <option value="defalut" selected={true}>please select a node.</option>
      </select>
    }
  }
}
