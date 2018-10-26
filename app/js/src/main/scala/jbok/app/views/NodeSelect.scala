package jbok.app.views

import jbok.app.AppState
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Constants
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLSelectElement

case class NodeSelect(state: AppState) {
  @binding.dom
  def render: Binding[Element] = {
    val onChangeHandler = { event: Event =>
      event.currentTarget match {
        case select: HTMLSelectElement =>
          val v = select.options(select.selectedIndex).value
          state.currentId.value = state.nodeInfos.value.get(v).map(_.id)
          println(s"currId: ${state.currentId.value.get}")
        case _ =>
      }
    }
    {
      <select id="nodeSelect" onchange={onChangeHandler}> {
        val nodeInfos = state.nodeInfos.bind
        for {
          node <- Constants(nodeInfos.toList.sortBy(_._2.rpcPort): _*)
          isSelected = if (state.currentId.value.isDefined && state.currentId.value.get == node._1) true else false
        } yield {
          <option value={node._1} selected={isSelected}>
            {node._2.addr.toString} {node._1.take(7)}
          </option>
        }}
        <option value="defalut" selected={true}>please select a node.</option>
      </select>
    }
  }
}
