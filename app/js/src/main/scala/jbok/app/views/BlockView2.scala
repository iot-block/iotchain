package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.components.Modal
import jbok.app.views.Nav.{Tab, TabList}
import jbok.core.models._
import jbok.crypto.signature.{ECDSA, Signature}
import org.scalajs.dom._
import scodec.bits.ByteVector
import scodec.bits._

import scala.scalajs.js.Date

case class BlockView2() {
  val header = BlockHeader(
    parentHash = hex"8345d132564b3660aa5f27c9415310634b50dbc92579c65a0825d9a255227a71",
    ommersHash = hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
    beneficiary = hex"df7d7e053933b5cc24372f878c90e62dadad5d42",
    stateRoot = hex"087f96537eba43885ab563227262580b27fc5e6516db79a6fc4d3bcd241dda67",
    transactionsRoot = hex"8ae451039a8bf403b899dcd23252d94761ddd23b88c769d9b7996546edc47fac",
    receiptsRoot = hex"8b472d8d4d39bae6a5570c2a42276ed2d6a56ac51a1a356d5b17c5564d01fd5d",
    logsBloom = ByteVector.fromValidHex("0" * 512),
    difficulty = BigInt("14005986920576"),
    number = 3125369,
    gasLimit = 4699996,
    gasUsed = 84000,
    unixTimestamp = 1486131165,
    extraData = hex"d5830104098650617269747986312e31332e30826c69",
    mixHash = hex"be90ac33b3f6d0316e60eef505ff5ec7333c9f3c85c1a36fc2523cd6b75ddb8a",
    nonce = hex"2b0fb0c002946392"
  )

  val body = BlockBody(
    transactionList = List[SignedTransaction](
      SignedTransaction(
        tx = Transaction(
          nonce = BigInt("438550"),
          gasPrice = BigInt("20000000000"),
          gasLimit = BigInt("50000"),
          receivingAddress = Address(hex"ee4439beb5c71513b080bbf9393441697a29f478"),
          value = BigInt("1265230129703017984"),
          payload = ByteVector.empty
        ),
        pointSign = 0x9d.toByte,
        signatureRandom = hex"5b496e526a65eac3c4312e683361bfdb873741acd3714c3bf1bcd7f01dd57ccb",
        signature = hex"3a30af5f529c7fc1d43cfed773275290475337c5e499f383afd012edcc8d7299",
        chainId = 0x3d.toByte
      ),
      SignedTransaction(
        tx = Transaction(
          nonce = BigInt("438551"),
          gasPrice = BigInt("20000000000"),
          gasLimit = BigInt("50000"),
          receivingAddress = Address(hex"c68e9954c7422f479e344faace70c692217ea05b"),
          value = BigInt("656010196207162880"),
          payload = ByteVector.empty
        ),
        pointSign = 0x9d.toByte,
        signatureRandom = hex"377e542cd9cd0a4414752a18d0862a5d6ced24ee6dba26b583cd85bc435b0ccf",
        signature = hex"579fee4fd96ecf9a92ec450be3c9a139a687aa3c72c7e43cfac8c1feaf65c4ac",
        chainId = 0x3d.toByte
      ),
      SignedTransaction(
        tx = Transaction(
          nonce = BigInt("438552"),
          gasPrice = BigInt("20000000000"),
          gasLimit = BigInt("50000"),
          receivingAddress = Address(hex"19c5a95eeae4446c5d24363eab4355157e4f828b"),
          value = BigInt("3725976610361427456"),
          payload = ByteVector.empty
        ),
        pointSign = 0x9d.toByte,
        signatureRandom = hex"a70267341ba0b33f7e6f122080aa767d52ba4879776b793c35efec31dc70778d",
        signature = hex"3f66ed7f0197627cbedfe80fd8e525e8bc6c5519aae7955e7493591dcdf1d6d2",
        chainId = 0x3d.toByte
      ),
      SignedTransaction(
        tx = Transaction(
          nonce = BigInt("438553"),
          gasPrice = BigInt("20000000000"),
          gasLimit = BigInt("50000"),
          receivingAddress = Address(hex"3435be928d783b7c48a2c3109cba0d97d680747a"),
          value = BigInt("108516826677274384"),
          payload = ByteVector.empty
        ),
        pointSign = 0x9d.toByte,
        signatureRandom = hex"beb8226bdb90216ca29967871a6663b56bdd7b86cf3788796b52fd1ea3606698",
        signature = hex"2446994156bc1780cb5806e730b171b38307d5de5b9b0d9ad1f9de82e00316b5",
        chainId = 0x3d.toByte
      )
    ),
    uncleNodesList = List[BlockHeader]()
  )

  val block: Var[Block] = Var(Block(header, body))

  val p = for {
    keyPair <- Signature[ECDSA].generateKeyPair()
    tx = Transaction(0, 0, 0, None, BigInt(10000), ByteVector.empty)
    stx <- SignedTransaction.signIO(tx, keyPair)
    _ = stxs.value += stx
  } yield ()

  val stxs: Vars[SignedTransaction] = Vars[SignedTransaction]()

  p.unsafeToFuture()

  @binding.dom
  val overview: Binding[Element] =
    <div>
      <table class="table-view">
        <tr>
          <th>Height:</th>
          <td>{block.bind.header.number.toString}</td>
        </tr>
        <tr>
          <th>TimeStamp:</th>
          <td>{new Date(block.bind.header.unixTimestamp).toDateString()}</td>
        </tr>
        <tr>
          <th>Transactions:</th>
          <td>{s"${block.bind.body.transactionList.size.toLong} transactions in this Block"}</td>
        </tr>
        <tr>
          <th>Hash:</th>
          <td>{block.bind.header.hash.toHex}</td>
        </tr>
        <tr>
          <th>Parent Hash:</th>
          <td>{block.bind.header.parentHash.toHex}</td>
        </tr>
        <tr>
          <th>Mined By:</th>
          <td>{block.bind.header.beneficiary.toHex}</td>
        </tr>
        <tr>
          <th>Difficulty:</th>
          <td>{block.bind.header.difficulty.toString}</td>
        </tr>
        <tr>
          <th>Gas Used:</th>
          <td>{block.bind.header.gasUsed.toString}</td>
        </tr>
        <tr>
          <th>Gas Limit:</th>
          <td>{block.bind.header.gasLimit.toString}</td>
        </tr>
        <tr>
          <th>Nonce:</th>
          <td>{block.bind.header.nonce.toHex}</td>
        </tr>
        <tr>
          <th>Extra Data:</th>
          <td>{block.bind.header.extraData.toHex}</td>
        </tr>
      </table>
    </div>

  @binding.dom
  val txsView: Binding[Element] =
    <div>
      <table class="table-view">
        <thead>
          <tr>
            <th>Tx Hash</th>
            <th>From</th>
            <th>To</th>
            <th>Value</th>
            <th>Detail</th>
          </tr>
        </thead>
        <tbody>{
          for (tx <- Constants(stxs.bind: _*)) yield {
            <tr>
              <td>
                <a>{tx.hash.toHex}</a>
              </td>
              <td>
                <a>{SignedTransaction.getSender(tx).get.toString}</a>
              </td>
              <td>
                <a>{tx.receivingAddress.toString}</a>
              </td>
              <td>
                {tx.value.toString}
              </td>
              <td>
                {Modal("view", TxView.render(tx)).render().bind}
              </td>
            </tr>
          }}
        </tbody>
      </table>
    </div>

  val tabs = Vars(
    Tab("Overview", overview, ""),
    Tab("Transactions", txsView, ""),
  )

  val tabList = TabList(tabs, Var(tabs.value.head))

  @binding.dom
  val left: Binding[Node] =
    <div class="nav-left">
      {
      for {
        tab <- tabList.tabs
      } yield {
        val isSelected = tabList.selected.bind == tab
        <div class={s"tab-small ${if (isSelected) "selected" else ""}"}
             onclick={_: Event => tabList.selected.value = tab}>
          { tab.name }
        </div>
      }
      }
    </div>

  @binding.dom
  val right: Binding[Node] =
    <div class="nav-right"></div>

  val navBar = Nav.render(left, right)

  @binding.dom
  def render: Binding[Element] =
    <div>
      {navBar.bind}
      <div>
        {
        for {
          tab <- tabList.tabs
        } yield {
          val isSelected = tabList.selected.bind == tab
          <div class={s"tab-content ${if (isSelected) "selected" else ""}"}>
            {tab.content.bind}
          </div>
        }
        }
      </div>
    </div>

}
