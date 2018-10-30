package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.AppState
import org.scalajs.dom.Event
import org.scalajs.dom.raw._

case class ConfigView(state: AppState) {
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

  val onToggleHandler = { event: Event =>
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

  val onInputHandler = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement => {
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
      }
      case _ =>
    }
  }

  val onClickAdvance = { event: Event =>
    event.currentTarget match {
      case _: HTMLButtonElement => {
        moreOption.value = !moreOption.value
      }
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
    </div>
}
