package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.BindingSeq
import jbok.app.components.Spin
import jbok.app.{AppState, ClientStatus}
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLInputElement

final case class StatusView(state: AppState) {
  @binding.dom
  def renderItem(title: String, value: String): Binding[Element] =
    <div class="status-item delimiter">
      <p class="status-title">{title}</p>
      {
        if(state.isLoading.loadingStatus.bind) {
          Spin.render().bind
        } else {
          <p class="status-value">{value}</p>
        }
      }
    </div>

  val onToggleHandler = (event: Event) =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        val v = input.checked
        state.update.value = v
      case _ =>
  }

  @binding.dom
  def renderStatus(status: ClientStatus): Binding[BindingSeq[Node]] =
    <div>
      {renderItem("current number", status.number.bind.toString).bind}
    </div>
    <div>
      {renderItem("gas price", status.gasPrice.bind.toString).bind}
    </div>
    <div>
      {renderItem("gas limit", status.gasLimit.bind.toString).bind}
    </div>
    <div>
      {renderItem("mining status", status.miningStatus.bind.toString).bind}
    </div>
    <div class="status-item delimiter">
      <p class="status-title">rpc server</p>
      <p class="status-value">
        {state.activeNode.value.flatMap(id => state.nodes.value.get(id).map(_.addr.toString)).getOrElse("No Select Node")}{state.clients.value.exists(c => c._1 == state.activeNode.value.getOrElse("")) match {
        case true => <span class="led-green"></span>
        case false => <span class="led-red"></span>
      }}
      </p>
    </div>
    <div class="status-item delimiter">
      <p class="status-title">auto update</p>
      <p class="status-value">
        {<label class="switch">
        <input type="checkbox" checked={state.update.bind} onchange={onToggleHandler}></input>
        <span class="slider"></span>
      </label>}
      </p>
    </div>

  @binding.dom
  def render: Binding[Element] =
    <div class="status">
    {
      state.activeNode.bind.flatMap(id => state.nodes.value.get(id).map(_.status)) match {
        case Some(status) => renderStatus(status).bind
        case _ => renderStatus(ClientStatus()).bind
      }
    }
    </div>
}
