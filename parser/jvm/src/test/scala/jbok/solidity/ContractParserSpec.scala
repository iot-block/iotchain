package jbok.solidity

import jbok.JbokSpec
import scodec.bits._
import io.circe.Json
import io.circe.syntax._
import jbok.solidity.visitors.ContractParser

class ContractParserSpec extends JbokSpec {
  val contractSourceCode =
    """
      |contract TokenERC20 {
      |    // Public variables of the token
      |    string public name = "IOT on Chain";
      |    string public symbol = "ITC";
      |    uint256 public decimals = 18;
      |    // 18 decimals is the strongly suggested default, avoid changing it
      |    uint256 public totalSupply = 100*1000*1000*10**decimals;
      |
      |    // This creates an array with all balances
      |    mapping (address => uint256) public balanceOf;
      |    mapping (address => mapping (address => uint256)) public allowance;
      |
      |    // This generates a public event on the blockchain that will notify clients
      |    event Transfer(address indexed from, address indexed to, uint256 value);
      |
      |    // This notifies clients about the amount burnt
      |    event Burn(address indexed from, uint256 value);
      |
      |    struct a {
      |        uint256 aaa;
      |        uint256 bbb;
      |    }
      |
      |    enum weekday {
      |        monday, tuesday, wednesday, thursday, friday, saturday, sunday
      |    }
      |
      |    /**
      |     * Constrctor function
      |     *
      |     * Initializes contract with initial supply tokens to the creator of the contract
      |     */
      |    function TokenERC20(
      |    ) public {
      |        balanceOf[msg.sender] = totalSupply;                // Give the creator all initial tokens
      |    }
      |
      |    /**
      |     * Internal transfer, only can be called by this contract
      |     */
      |    function _transfer(address _from, address _to, uint _value) internal {
      |        // Prevent transfer to 0x0 address. Use burn() instead
      |        require(_to != 0x0);
      |        // Check if the sender has enough
      |        require(balanceOf[_from] >= _value);
      |        // Check for overflows
      |        require(balanceOf[_to] + _value > balanceOf[_to]);
      |        // Save this for an assertion in the future
      |        uint previousBalances = balanceOf[_from] + balanceOf[_to];
      |        // Subtract from the sender
      |        balanceOf[_from] -= _value;
      |        // Add the same to the recipient
      |        balanceOf[_to] += _value;
      |        Transfer(_from, _to, _value);
      |        // Asserts are used to use static analysis to find bugs in your code. They should never fail
      |        assert(balanceOf[_from] + balanceOf[_to] == previousBalances);
      |    }
      |
      |    /**
      |     * Transfer tokens
      |     *
      |     * Send `_value` tokens to `_to` from your account
      |     *
      |     * @param _to The address of the recipient
      |     * @param _value the amount to send
      |     */
      |    function transfer(address _to, uint256 _value) public {
      |        _transfer(msg.sender, _to, _value);
      |    }
      |
      |    /**
      |     * Transfer tokens from other address
      |     *
      |     * Send `_value` tokens to `_to` in behalf of `_from`
      |     *
      |     * @param _from The address of the sender
      |     * @param _to The address of the recipient
      |     * @param _value the amount to send
      |     */
      |    function transferFrom(address _from, address _to, uint256 _value) public returns (bool success) {
      |        require(_value <= allowance[_from][msg.sender]);     // Check allowance
      |        allowance[_from][msg.sender] -= _value;
      |        _transfer(_from, _to, _value);
      |        return true;
      |    }
      |
      |    /**
      |     * Set allowance for other address
      |     *
      |     * Allows `_spender` to spend no more than `_value` tokens in your behalf
      |     *
      |     * @param _spender The address authorized to spend
      |     * @param _value the max amount they can spend
      |     */
      |    function approve(address _spender, uint256 _value) public
      |        returns (bool success) {
      |        allowance[msg.sender][_spender] = _value;
      |        return true;
      |    }
      |
      |    /**
      |     * Set allowance for other address and notify
      |     *
      |     * Allows `_spender` to spend no more than `_value` tokens in your behalf, and then ping the contract about it
      |     *
      |     * @param _spender The address authorized to spend
      |     * @param _value the max amount they can spend
      |     * @param _extraData some extra information to send to the approved contract
      |     */
      |    function approveAndCall(address _spender, uint256 _value, bytes _extraData)
      |        public
      |        returns (bool success) {
      |        tokenRecipient spender = tokenRecipient(_spender);
      |        if (approve(_spender, _value)) {
      |            spender.receiveApproval(msg.sender, _value, this, _extraData);
      |            return true;
      |        }
      |    }
      |
      |    /**
      |     * Destroy tokens
      |     *
      |     * Remove `_value` tokens from the system irreversibly
      |     *
      |     * @param _value the amount of money to burn
      |     */
      |    function burn(uint256 _value) public returns (bool success) {
      |        require(balanceOf[msg.sender] >= _value);   // Check if the sender has enough
      |        balanceOf[msg.sender] -= _value;            // Subtract from the sender
      |        totalSupply -= _value;                      // Updates totalSupply
      |        Burn(msg.sender, _value);
      |        return true;
      |    }
      |
      |    /**
      |     * Destroy tokens from other account
      |     *
      |     * Remove `_value` tokens from the system irreversibly on behalf of `_from`.
      |     *
      |     * @param _from the address of the sender
      |     * @param _value the amount of money to burn
      |     */
      |    function burnFrom(address _from, uint256 _value) public returns (bool success) {
      |        require(balanceOf[_from] >= _value);                // Check if the targeted balance is enough
      |        require(_value <= allowance[_from][msg.sender]);    // Check allowance
      |        balanceOf[_from] -= _value;                         // Subtract from the targeted balance
      |        allowance[_from][msg.sender] -= _value;             // Subtract from the sender's allowance
      |        totalSupply -= _value;                              // Update totalSupply
      |        Burn(_from, _value);
      |        return true;
      |    }
      |}
    """.stripMargin

  "ContractParserSpec" should {
    "test" in {
      val parseResult = ContractParser.parse(contractSourceCode)
      parseResult.isRight shouldBe true
      val contracts    = parseResult.right.get
      val erc20Methods = contracts.map(_.toABI).head.methods.map(method => method.name -> method).toMap

      val name = erc20Methods("name")
        .decode(
          hex"0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000c494f54206f6e20436861696e0000000000000000000000000000000000000000")
      name.isRight shouldBe true
      name.right.get shouldBe List(Json.fromString("IOT on Chain")).asJson

      val symbol = erc20Methods("symbol")
        .decode(
          hex"0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000034954430000000000000000000000000000000000000000000000000000000000")
      symbol.isRight shouldBe true
      symbol.right.get shouldBe List(Json.fromString("ITC")).asJson

      val totalSupply =
        erc20Methods("totalSupply").decode(hex"0x00000000000000000000000000000000000000000052b7d2dcc80cd2e4000000")
      totalSupply.isRight shouldBe true
      totalSupply.right.get shouldBe List(Json.fromBigInt(BigInt(10).pow(26))).asJson

      val balanceOfPayload = erc20Methods("balanceOf").encode("[\"0x1234567890123456789012345678901234567890\"]")
      balanceOfPayload.isRight shouldBe true
      balanceOfPayload.right.get shouldBe hex"0x70a082310000000000000000000000001234567890123456789012345678901234567890"

      val allowance = erc20Methods("allowance")
        .encode("[\"0x1234567890123456789012345678901234567890\", \"0x0987654321098765432109876543210987654321\"]")
      allowance.isRight shouldBe true
      allowance.right.get shouldBe hex"0xdd62ed3e00000000000000000000000012345678901234567890123456789012345678900000000000000000000000000987654321098765432109876543210987654321"

      val transfer = erc20Methods("transfer").encode("[\"0x1234567890123456789012345678901234567890\", 233333]")
      transfer.isRight shouldBe true
      transfer.right.get shouldBe hex"0xa9059cbb00000000000000000000000012345678901234567890123456789012345678900000000000000000000000000000000000000000000000000000000000038f75"

      val transferFrom = erc20Methods("transferFrom")
        .encode(
          "[\"0x1234567890123456789012345678901234567890\", \"0x0987654321098765432109876543210987654321\", 233333]")
      transferFrom.isRight shouldBe true
      transferFrom.right.get shouldBe hex"0x23b872dd000000000000000000000000123456789012345678901234567890123456789000000000000000000000000009876543210987654321098765432109876543210000000000000000000000000000000000000000000000000000000000038f75"

      val approve = erc20Methods("approve").encode("[\"0x1234567890123456789012345678901234567890\", 1234]")
      approve.isRight shouldBe true
      approve.right.get shouldBe hex"0x095ea7b3000000000000000000000000123456789012345678901234567890123456789000000000000000000000000000000000000000000000000000000000000004d2"

      val approveAndCall =
        erc20Methods("approveAndCall").encode("[\"0x1234567890123456789012345678901234567890\", 4321, \"1234\"]")
      approveAndCall.isRight shouldBe true
      approveAndCall.right.get shouldBe hex"0xcae9ca51000000000000000000000000123456789012345678901234567890123456789000000000000000000000000000000000000000000000000000000000000010e1000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000021234000000000000000000000000000000000000000000000000000000000000"

      val burn = erc20Methods("burn").encode("[9999]")
      burn.isRight shouldBe true
      burn.right.get shouldBe hex"0x42966c68000000000000000000000000000000000000000000000000000000000000270f"

      val burnFrom = erc20Methods("burnFrom").encode("[\"0x1234567890123456789012345678901234567890\", 8888]")
      burnFrom.isRight shouldBe true
      burnFrom.right.get shouldBe hex"0x79cc6790000000000000000000000000123456789012345678901234567890123456789000000000000000000000000000000000000000000000000000000000000022b8"
    }
  }
}
