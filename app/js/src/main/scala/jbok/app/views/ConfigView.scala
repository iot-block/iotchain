package jbok.app.views

import cats.effect.IO
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import com.thoughtworks.binding.Binding
import jbok.app.components.{Input, Spin}
import jbok.app.helper.InputValidator
import jbok.app.{AppState, JbokClientPlatform}
import jbok.app.execution._
//import jbok.sdk.api.NodeInfo
import org.scalajs.dom.Event
import org.scalajs.dom.raw._

final case class ConfigView(state: AppState) {
  val interfaces: Vars[String]       = Vars.empty[String]
  val moreOption: Var[Boolean]       = Var(false)
  val httpServerSwitch: Var[Boolean] = Var(false)
  val description: Vars[String]      = Vars.empty[String]
  val peerListenInput                = Input("peerListenPort", validator = InputValidator.isValidPort)
  val httpPortInput                  = Input("httpPort", validator = InputValidator.isValidPort)
  val hostInput                      = Input("hostName", defaultValue = "127.0.0.1", disabled = true)
  val portInput                      = Input("hostPort", defaultValue = "8888", validator = InputValidator.isValidPort)

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
        <div class="row config-row">
          <div class="config-item">
            <label for="peerListenPort">
              <b>
                peer listen port
              </b>
            </label>
            {peerListenInput.render.bind}
          </div>
          <div class="config-item">
            <p>
              <br/><br/>
              {"Must be > 1000 and < 65535."}
            </p>
          </div>
        </div>

        <div class="row config-row">
          <label for="httpServerSwitch"><b>http server</b></label>
          <div class ="config-item">
            <label class="switch">
              <input name ="httpServerSwitch" type="checkbox" checked={httpServerSwitch.bind} onchange={onToggleHandler}></input>
              <span class="slider"></span>
            </label>
          </div>
          <div class ="config-item">
            <p></p>
          </div>
        </div>

        {
          if (httpServerSwitch.bind) {
            <div class="row config-row">
              <div class="config-item">
                <label for="httpPort">
                  <b>
                    http server port
                  </b>
                </label>
                {httpPortInput.render.bind}
              </div>
              <div class="config-item">
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

  private val onClickAdvance = { event: Event =>
    event.currentTarget match {
      case _: HTMLButtonElement =>
        moreOption.value = !moreOption.value
      case _ =>
    }
  }

  @binding.dom
  def render: Binding[Element] =
    <div class="config">
      <h1>config</h1>
      <div class="row config-row">
        <div class ="config-item">
          <label for="hostname">
            <b>
              hostname
            </b>
          </label>
          {hostInput.render.bind}
        </div>
        <div class ="config-item">
          <p> <br/><br/>The server will accept RPC connections on the following host. </p>
        </div>
      </div>

      <div class="row config-row">
        <div class ="config-item">
          <label for="port">
            <b>
              port
            </b>
          </label>
          {portInput.render.bind}
        </div>
        <div class ="config-item">
          <p> <br/><br/>The server will accept RPC connections on the port. </p>
        </div>
      </div>

      <div class="row config-row">
          <button onclick={onClickAdvance}>Advance</button>
      </div>

      {renderMoreConfig().bind}

      <div class="row config-row">
        <button>Restart</button>
        <button>Cancel</button>
      </div>

      {renderSimulationAdd.bind}
    </div>

  val connectedStatus: Var[Option[String]] = Var(None)
  val addHostInput                         = Input("addHostInput", defaultValue = "127.0.0.1", validator = InputValidator.isValidIPv4)
  val addPortInput                         = Input("addPortInput", defaultValue = "30316", validator = InputValidator.isValidPort)

  private val onClickAdd = { event: Event =>
    event.currentTarget match {
      case _: HTMLButtonElement =>
        connectedStatus.value = Some("connecting")
        if (addHostInput.isValid && addPortInput.isValid) {
          val rpcAddr = s"http://${addHostInput.value}:${addPortInput.value}"
          val p = for {
            client      <- IO.delay(JbokClientPlatform.apply[IO](rpcAddr))
            peerNodeUri <- client.admin.peerUri
            _ = state.clients.value += (peerNodeUri -> client)
            _ = state.addNode(addPortInput.value, addHostInput.value, addPortInput.value.toInt)
            _ = connectedStatus.value = Some("connected.")
          } yield ()

          p.timeout(state.config.value.clientTimeout)
            .handleErrorWith(e => IO.delay(connectedStatus.value = Some(s"connect failed: ${e}\n ${e.getStackTrace.mkString("\n")}")))
            .unsafeToFuture()
        }
      case _ =>
    }

  }

  @binding.dom
  def renderSimulationAdd: Binding[Element] =
    <div>
      <h1>simulation</h1>
  
      <div class ="row config-row">
        <div class ="config-item">
          <label for="add-hostname">
            <b>
              hostname
            </b>
          </label>
          {addHostInput.render.bind}
        </div>
        <div class ="config-item">
          <p> <br/><br/>The server to connect host. </p>
        </div>
      </div>
  
      <div class="row config-row">
        <div class ="config-item">
          <label for="add-port">
            <b>
              port
            </b>
          </label>
          {addPortInput.render.bind}
        </div>
        <div class ="config-item">
          <p> <br/><br/>The server to connect port. </p>
        </div>
      </div>

      {
        connectedStatus.bind match {
          case None => <div/>
          case Some(status) if status == "connecting" =>
            <div class="row config-row">
              <div class="config-item">
                <label><b> status: </b></label>
              </div>
              <div class="config-item">
                {Spin.render().bind}
              </div>
            </div>
          case Some(status) =>
            <div class="row config-row">
              <div class="config-item">
                <label><b> status: </b></label>
              </div>
              <div class="config-item">
              {status}
              </div>
            </div>
        }
      }
      
      <div class="row config-row">
        <button onclick={onClickAdd} >Add</button>
      </div>
    </div>
}
