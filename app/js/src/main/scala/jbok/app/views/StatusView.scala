package jbok.app.views

import java.net.URI

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Var
import jbok.app.{AppState, JbokClient}
import org.scalajs.dom._

case class StatusView(state: AppState) {

//  def fetch() = {
//    val p = for {
//      bestNumber <- client.public.bestBlockNumber
//      _ = status.number.value = bestNumber
//    } yield ()
//    p.unsafeToFuture()
//  }
//
//  fetch()

  @binding.dom
  def renderItem(title: String, value: String): Binding[Element] = {
    <div class="status-item delimiter">
      <p class="status-title">{title}</p>
      <p class="status-value">{value}</p>
    </div>
  }

  @binding.dom
  def render: Binding[Element] = {
    <div class="status">
      {renderItem("current number", state.number.bind.toString).bind}
      {renderItem("gas price", state.gasPrice.bind.toString).bind}
      {renderItem("gas limit", state.gasLimit.bind.toString).bind}
      {renderItem("mining status", state.miningStatus.bind).bind}
      <div class="status-item delimiter">
        <p class="status-title">rpc server</p>
        <p class="status-value">
          {state.config.bind.uri.toString}
          {
            state.client.bind match {
              case Some(_) => <span class="led-green"></span>
              case _ => <span class="led-red"></span>
            }
          }
        </p>
      </div>
    </div>
  }
}
