package jbok.app.views

import java.net._

import cats.effect.IO
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import com.thoughtworks.binding.{Binding, FutureBinding}
import jbok.app.api.NodeInfo
import jbok.app.components.Spinner
import jbok.app.{AppState, SimuClient}
import jbok.common.execution._
import org.scalajs.dom.Event
import org.scalajs.dom.raw._

@SuppressWarnings(Array(
  "org.wartremover.warts.OptionPartial",
  "org.wartremover.warts.EitherProjectionPartial",
))
final case class ConfigView(state: AppState) {
  val interfaces: Vars[String]       = Vars.empty[String]
  val host: Var[String]              = Var("127.0.0.1")
  val hostIsValid: Var[Boolean]      = Var(true)
  val port: Var[String]              = Var("8333")
  val portIsValid: Var[Boolean]      = Var(true)
  val moreOption: Var[Boolean]       = Var(false)
  val httpServerSwitch: Var[Boolean] = Var(false)
  val httpServerPort: Var[String]    = Var("")
  val httpPortIsValid: Var[Boolean]  = Var(true)
  val peerListenPort: Var[String]    = Var("8888")
  val peerPortIsValid: Var[Boolean]  = Var(true)
  val description: Vars[String]      = Vars.empty[String]

  def init(): Unit = {
    // get all system interface
  }

  init()

  private val onToggleHandler = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        httpServerSwitch.value = input.checked
      case _ =>
    }
  }

  @binding.dom
  def renderMoreConfig(): Binding[Element] =
    if (moreOption.bind) {
      <div>
        <div class="config-row">
          <div class="config-row-item">
            <label for="peerListenPort">
              <b>
                peer listen port
              </b>
            </label>
            <input name="peerListenPort" type="text" oninput={onInputHandler} value={peerListenPort.bind} class={if(peerPortIsValid.bind) "valid" else "invalid"}/>
          </div>
          <div class="config-row-item">
            <p>
              <br/><br/>
              {"Must be > 1000 and < 65535."}
            </p>
          </div>
        </div>

        <div class="config-row">
          <label for="httpServerSwitch"><b>http server</b></label>
          <div class ="config-row-item">
            <label class="switch">
              <input name ="httpServerSwitch" type="checkbox" checked={httpServerSwitch.bind} onchange={onToggleHandler}></input>
              <span class="slider"></span>
            </label>
          </div>
          <div class ="config-row-item">
            <p></p>
          </div>
        </div>

        {
          if (httpServerSwitch.bind) {
            <div class="config-row">
              <div class="config-row-item">
                <label for="httpPort">
                  <b>
                    http server port
                  </b>
                </label>
                <input name="httpPort" type="text" oninput={onInputHandler} value={httpServerPort.bind} class={if(httpPortIsValid.bind) "valid" else "invalid"} disabled={!httpServerSwitch.bind}/>
              </div>
              <div class="config-row-item">
                <p>
                  <br/><br/>
                  {"Must be > 1000 and < 65535."}
                </p>
              </div>
            </div>
          } else {
            <div></div>
          }
        }
      </div>
    } else {
      <div></div>
    }

  private val onInputHandler = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        input.name match {
          case "hostname" => host.value = input.value.trim
          case "port" =>
            port.value = input.value.trim
            portIsValid.value = InputValidator.isValidPort(port.value)
          case "webSocketPort" =>
            peerListenPort.value = input.value.trim
            peerPortIsValid.value = InputValidator.isValidPort(peerListenPort.value)
          case "httpPort" =>
            httpServerPort.value = input.value.trim
            httpPortIsValid.value = InputValidator.isValidPort(httpServerPort.value)
        }
        port.value = input.value.trim
      case _ =>
    }
  }

  private val onClickAdvance = { event: Event =>
    event.currentTarget match {
      case _: HTMLButtonElement =>
        moreOption.value = !moreOption.value
      case _ =>
    }
  }

  @binding.dom
  def render(): Binding[Element] =
    <div class="config">
      <h1>config</h1>
      <div class="config-row">
        <div class ="config-row-item">
          <label for="hostname">
            <b>
              hostname
            </b>
          </label>
          <input name="hostname" type="text" oninput={onInputHandler} value={host.bind} disabled={true}/>
        </div>
        <div class ="config-row-item">
          <p> <br/><br/>The server will accept RPC connections on the following host. </p>
        </div>
      </div>

      <div class="config-row">
        <div class ="config-row-item">
          <label for="port">
            <b>
              port
            </b>
          </label>
          <input name="port" type="text" oninput={onInputHandler} value={port.bind} class={if(portIsValid.bind) "valid" else "invalid"}/>
        </div>
        <div class ="config-row-item">
          <p> <br/><br/>The server will accept RPC connections on the port. </p>
        </div>
      </div>

      <div class="config-row">
          <button onclick={onClickAdvance}>Advance</button>
      </div>

      {renderMoreConfig().bind}

      <div class="config-row">
        <button>Restart</button>
        <button>Cancel</button>
      </div>

      {renderSimulationAdd.bind}
    </div>

  val addHost: Var[String]                     = Var("127.0.0.1")
  val addHostIsValid: Var[Boolean]             = Var(true)
  val addPort: Var[String]                     = Var("30316")
  val addPortIsValid: Var[Boolean]             = Var(true)
  val connectedStatus: Var[Option[IO[String]]] = Var(None)

  private val onInputHandlerAdd = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        input.name match {
          case "add-hostname" =>
            addHost.value = input.value.trim
            addHostIsValid.value = InputValidator.isValidIPv4(addHost.value)
          case "add-port" =>
            addPort.value = input.value.trim
            addPortIsValid.value = InputValidator.isValidPort(addPort.value)
        }
        port.value = input.value.trim
      case _ =>
    }
  }

  private val onClickAdd = { event: Event =>
    event.currentTarget match {
      case _: HTMLButtonElement =>
        if (addHostIsValid.value && addPortIsValid.value) {
          val rpcAddr = s"${addHost.value}:${addPort.value}"
          val p = for {
            sc             <- SimuClient(state.config.value.uri)
            peerNodeUriOpt <- sc.simulation.addNode(addHost.value, addPort.value.toInt)
            tip <- if (peerNodeUriOpt.isEmpty) {
              IO.pure("connect failed.")
            } else {
              val nodeInfo = NodeInfo(peerNodeUriOpt.get, addHost.value, addPort.value.toInt)
              state.addNodeInfo(nodeInfo)
              for {
                jbokClient <- jbok.app.client.JbokClient(new URI(nodeInfo.rpcAddr))
                _ = state.clients.value += (peerNodeUriOpt.get -> jbokClient)
              } yield "connected."
            }
          } yield tip

          connectedStatus.value = Some(p)
        }
      case _ =>
    }

  }

  @binding.dom
  def renderSimulationAdd: Binding[Element] =
    <div>
      <h1>simulation</h1>
  
      <div class ="config-row">
        <div class ="config-row-item">
          <label for="add-hostname">
            <b>
              hostname
            </b>
          </label>
          <input name="add-hostname" type="text" oninput={onInputHandlerAdd} value={addHost.bind} class={if(addHostIsValid.bind) "valid" else "invalid"} />
        </div>
        <div class ="config-row-item">
          <p> <br/><br/>The server to connect host. </p>
        </div>
      </div>
  
      <div class="config-row">
        <div class ="config-row-item">
          <label for="add-port">
            <b>
              port
            </b>
          </label>
          <input name="add-port" type="text" oninput={onInputHandlerAdd} value={addPort.bind} class={if(addPortIsValid.bind) "valid" else "invalid"} />
        </div>
        <div class ="config-row-item">
          <p> <br/><br/>The server to connect port. </p>
        </div>
      </div>

      {
        connectedStatus.bind match {
          case None => <div/>
          case Some(status) =>
            <div class="config-row">
              <div class="config-row-item">
                <b> status: </b>
              </div>
              <div class="config-row-item">
              {Spinner.render(FutureBinding(status.unsafeToFuture())).bind}
              </div>
            </div>
        }
      }
      
      <div class="config-row">
        <button onclick={onClickAdd} disabled={!(addHostIsValid.bind && addPortIsValid.bind)}>Add</button>
      </div>
    </div>
}
