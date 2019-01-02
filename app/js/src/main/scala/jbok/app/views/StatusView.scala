package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import jbok.app.{AppState, ClientStatus}
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLInputElement

final case class StatusView(state: AppState) {
  @binding.dom
  def renderItem(title: String, value: String): Binding[Element] =
    <div class="status-item delimiter">
      <p class="status-title">{title}</p>
      <p class="status-value">{value}</p>
    </div>

  @binding.dom
  def render: Binding[Element] = {
    val onToggleHandler = { event: Event =>
      event.currentTarget match {
        case input: HTMLInputElement =>
          val v = input.checked
          state.update.value = v
        case _ =>
      }
    }
    <div class="status">
      {
        val status = state.currentId.bind match {
          case Some(id) => state.status.value.getOrElse(id, ClientStatus())
          case _ => ClientStatus()
        }
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
            {
              state.currentId.value.map(id => state.nodeInfos.value(id).rpcAddr.toString).getOrElse("No Select Node")
            }
            {
              state.clients.value.exists(c => c._1 == state.currentId.value.getOrElse("")) match {
                case true => <span class="led-green"></span>
                case false => <span class="led-red"></span>
              }
            }
          </p>
        </div>
        <div class="status-item delimiter">
          <p class="status-title">auto update</p>
          <p class="status-value">
            {
              <label class="switch">
              <input type="checkbox" checked={state.update.bind} onchange={onToggleHandler}></input>
              <span class="slider"></span>
              </label>
            }
          </p>
        </div>
      }
    </div>
  }
}
