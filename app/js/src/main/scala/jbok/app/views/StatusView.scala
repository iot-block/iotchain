package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Var
import jbok.app.JbokClient
import org.scalajs.dom._

case class Status(number: Var[BigInt], gasPrice: Var[BigInt], gasLimit: Var[BigInt], rpcServer: Var[String], miningStatus: Var[String])

class StatusView(client: JbokClient) {
  val uri = client.uri
  val status = Status(
    Var(BigInt(0)),
    Var(BigInt(0)),
    Var(BigInt(0)),
    Var(uri.toString),
    Var(s"idle"),
  )

  def fetch() = {
    val p = for {
      bestNumber <- client.public.bestBlockNumber
      _ = status.number.value = bestNumber
    } yield ()
    p.unsafeToFuture()
  }

  fetch()

  @binding.dom
  def renderItem(title: String, value: String): Binding[Element] = {
    <div class="status-item delimiter">
      <p class="status-title">{title}</p>
      <p class="status-value">{value}</p>
    </div>
  }

  @binding.dom
  def render(status: Status = status): Binding[Element] = {
    <div class="status">
      {renderItem("current number", status.number.bind.toString).bind}
      {renderItem("gas price", status.gasPrice.bind.toString).bind}
      {renderItem("gas limit", status.gasLimit.bind.toString).bind}
      {renderItem("rpc server", status.rpcServer.bind).bind}
      {renderItem("mining status", status.miningStatus.bind).bind}
    </div>
  }
}
