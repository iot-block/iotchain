package jbok.evm.solidity

import jbok.common.CommonSpec
import scodec.bits._
import io.circe.syntax._
import jbok.codec.json.implicits._
import jbok.evm.solidity.Ast.ModifierList
import jbok.evm.solidity.Ast.ModifierList.{Invocation, Public, View}

class SolidityParserSpec extends CommonSpec {
  val code =
    """contract TokenERC20 {
      |    event LogNote(
      |    address indexed from,
      |    address indexed to,
      |    uint256 value
      |    ) anonymous;
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
      |    function TokenERC20() public {
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
      |    function approve(address _spender, uint256 _value) modifier2 public
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
      |        public modifier1
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
      |    function burn(uint256 _value) onlyBy public onlyOwn returns (bool success) {
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
      |    function (uint256) external returns(uint256) ffff;
      |}
    """.stripMargin

  val code2 =
    """contract Vaccine {
      |    address public minter;
      |
      |    uint256 private recoderAmount;
      |
      |    mapping (string => string) private values;
      |
      |    constructor() public {
      |        minter = msg.sender;
      |    }
      |
      |    function setValue(string memory key, string memory newValue) public onlyOwner {
      |        require(msg.sender == minter,"没有权限");
      |        require(bytes(key).length != 0,"invalid key");
      |        require(bytes(newValue).length != 0,"invalid value");
      |
      |        if(bytes(values[key]).length==0){
      |            recoderAmount++;
      |        }
      |
      |        values[key] = newValue;
      |    }
      |
      |    function batchSetValues(string[] memory keys,string[] memory newValues) public onlyOwner {
      |
      |        require(keys.length == newValues.length,"invalid keys and values");
      |
      |        for (uint i = 0;i<keys.length;i++) {
      |
      |            require(bytes(keys[i]).length != 0,"invalid key");
      |            require(bytes(newValues[i]).length != 0,"invalid value");
      |
      |            if(bytes(values[keys[i]]).length==0){
      |            recoderAmount++;
      |        }
      |
      |            values[keys[i]] = newValues[i];
      |        }
      |    }
      |
      |    function getValue(string memory key) public onlyOwner view returns (string memory){
      |
      |        return values[key];
      |    }
      |
      |    function batchGetValues(string[] memory keys) public onlyOwner view returns (string[] memory){
      |
      |        string[] memory list = new string[](keys.length);
      |        for (uint i = 0;i<keys.length;i++) {
      |            list[i] = values[keys[i]];
      |        }
      |        return list;
      |    }
      |
      |    function getRecoderAmount() public view onlyOwner returns(uint256 ){
      |
      |        return recoderAmount;
      |    }
      |
      |    modifier onlyOwner {
      |        require(msg.sender == minter,"No Permission");
      |        _;
      |    }
      |}
    """.stripMargin

  val eosCode =
    """contract DSNote {
      |    event LogNote(
      |        bytes4   indexed  sig,
      |        address  indexed  guy,
      |        bytes32  indexed  foo,
      |        bytes32  indexed  bar,
      |	       uint	 	  wad,
      |        bytes             fax
      |    ) anonymous;
      |
      |    modifier note {
      |        bytes32 foo;
      |        bytes32 bar;
      |
      |        assembly {
      |            foo := calldataload(4)
      |            bar := calldataload(36)
      |        }
      |
      |        LogNote(msg.sig, msg.sender, foo, bar, msg.value, msg.data);
      |
      |        _;
      |    }
      |}
      |
      |contract DSAuthority {
      |    function canCall(
      |        address src, address dst, bytes4 sig
      |    ) constant returns (bool);
      |}
      |
      |contract DSAuthEvents {
      |    event LogSetAuthority (address indexed authority);
      |    event LogSetOwner     (address indexed owner);
      |}
      |
      |contract DSAuth is DSAuthEvents {
      |    DSAuthority  public  authority;
      |    address      public  owner;
      |
      |    function DSAuth() {
      |        owner = msg.sender;
      |        LogSetOwner(msg.sender);
      |    }
      |
      |    function setOwner(address owner_)
      |        auth
      |    {
      |        owner = owner_;
      |        LogSetOwner(owner);
      |    }
      |
      |    function setAuthority(DSAuthority authority_)
      |        auth
      |    {
      |        authority = authority_;
      |        LogSetAuthority(authority);
      |    }
      |
      |    modifier auth {
      |        assert(isAuthorized(msg.sender, msg.sig));
      |        _;
      |    }
      |
      |    modifier authorized(bytes4 sig) {
      |        assert(isAuthorized(msg.sender, sig));
      |        _;
      |    }
      |
      |    function isAuthorized(address src, bytes4 sig) internal returns (bool) {
      |        if (src == address(this)) {
      |            return true;
      |        } else if (src == owner) {
      |            return true;
      |        } else if (authority == DSAuthority(0)) {
      |            return false;
      |        } else {
      |            return authority.canCall(src, this, sig);
      |        }
      |    }
      |
      |    function assert(bool x) internal {
      |        if (!x) throw;
      |    }
      |}
      |
      |contract DSStop is DSAuth, DSNote {
      |
      |    bool public stopped;
      |
      |    modifier stoppable {
      |        assert (!stopped);
      |        _;
      |    }
      |    function stop() auth note {
      |        stopped = true;
      |    }
      |    function start() auth note {
      |        stopped = false;
      |    }
      |
      |}
      |
      |contract DSMath {
      |
      |    /*
      |    standard uint256 functions
      |     */
      |
      |    function add(uint256 x, uint256 y) constant internal returns (uint256 z) {
      |        assert((z = x + y) >= x);
      |    }
      |
      |    function sub(uint256 x, uint256 y) constant internal returns (uint256 z) {
      |        assert((z = x - y) <= x);
      |    }
      |
      |    function mul(uint256 x, uint256 y) constant internal returns (uint256 z) {
      |        assert((z = x * y) >= x);
      |    }
      |
      |    function div(uint256 x, uint256 y) constant internal returns (uint256 z) {
      |        z = x / y;
      |    }
      |
      |    function min(uint256 x, uint256 y) constant internal returns (uint256 z) {
      |        return x <= y ? x : y;
      |    }
      |    function max(uint256 x, uint256 y) constant internal returns (uint256 z) {
      |        return x >= y ? x : y;
      |    }
      |
      |    /*
      |    uint128 functions (h is for half)
      |     */
      |
      |
      |    function hadd(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        assert((z = x + y) >= x);
      |    }
      |
      |    function hsub(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        assert((z = x - y) <= x);
      |    }
      |
      |    function hmul(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        assert((z = x * y) >= x);
      |    }
      |
      |    function hdiv(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        z = x / y;
      |    }
      |
      |    function hmin(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        return x <= y ? x : y;
      |    }
      |    function hmax(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        return x >= y ? x : y;
      |    }
      |
      |
      |    /*
      |    int256 functions
      |     */
      |
      |    function imin(int256 x, int256 y) constant internal returns (int256 z) {
      |        return x <= y ? x : y;
      |    }
      |    function imax(int256 x, int256 y) constant internal returns (int256 z) {
      |        return x >= y ? x : y;
      |    }
      |
      |    /*
      |    WAD math
      |     */
      |
      |    uint128 constant WAD = 10 ** 18;
      |
      |    function wadd(uint128 x, uint128 y) constant internal returns (uint128) {
      |        return hadd(x, y);
      |    }
      |
      |    function wsub(uint128 x, uint128 y) constant internal returns (uint128) {
      |        return hsub(x, y);
      |    }
      |
      |    function wmul(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        z = cast((uint256(x) * y + WAD / 2) / WAD);
      |    }
      |
      |    function wdiv(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        z = cast((uint256(x) * WAD + y / 2) / y);
      |    }
      |
      |    function wmin(uint128 x, uint128 y) constant internal returns (uint128) {
      |        return hmin(x, y);
      |    }
      |    function wmax(uint128 x, uint128 y) constant internal returns (uint128) {
      |        return hmax(x, y);
      |    }
      |
      |    /*
      |    RAY math
      |     */
      |
      |    uint128 constant RAY = 10 ** 27;
      |
      |    function radd(uint128 x, uint128 y) constant internal returns (uint128) {
      |        return hadd(x, y);
      |    }
      |
      |    function rsub(uint128 x, uint128 y) constant internal returns (uint128) {
      |        return hsub(x, y);
      |    }
      |
      |    function rmul(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        z = cast((uint256(x) * y + RAY / 2) / RAY);
      |    }
      |
      |    function rdiv(uint128 x, uint128 y) constant internal returns (uint128 z) {
      |        z = cast((uint256(x) * RAY + y / 2) / y);
      |    }
      |
      |    function rpow(uint128 x, uint64 n) constant internal returns (uint128 z) {
      |        // This famous algorithm is called "exponentiation by squaring"
      |        // and calculates x^n with x as fixed-point and n as regular unsigned.
      |        //
      |        // It's O(log n), instead of O(n) for naive repeated multiplication.
      |        //
      |        // These facts are why it works:
      |        //
      |        //  If n is even, then x^n = (x^2)^(n/2).
      |        //  If n is odd,  then x^n = x * x^(n-1),
      |        //   and applying the equation for even x gives
      |        //    x^n = x * (x^2)^((n-1) / 2).
      |        //
      |        //  Also, EVM division is flooring and
      |        //    floor[(n-1) / 2] = floor[n / 2].
      |
      |        z = n % 2 != 0 ? x : RAY;
      |
      |        for (n /= 2; n != 0; n /= 2) {
      |            x = rmul(x, x);
      |
      |            if (n % 2 != 0) {
      |                z = rmul(z, x);
      |            }
      |        }
      |    }
      |
      |    function rmin(uint128 x, uint128 y) constant internal returns (uint128) {
      |        return hmin(x, y);
      |    }
      |    function rmax(uint128 x, uint128 y) constant internal returns (uint128) {
      |        return hmax(x, y);
      |    }
      |
      |    function cast(uint256 x) constant internal returns (uint128 z) {
      |        assert((z = uint128(x)) == x);
      |    }
      |
      |}
      |
      |contract ERC20 {
      |    function totalSupply() constant returns (uint supply);
      |    function balanceOf( address who ) constant returns (uint value);
      |    function allowance( address owner, address spender ) constant returns (uint _allowance);
      |
      |    function transfer( address to, uint value) returns (bool ok);
      |    function transferFrom( address from, address to, uint value) returns (bool ok);
      |    function approve( address spender, uint value ) returns (bool ok);
      |
      |    event Transfer( address indexed from, address indexed to, uint value);
      |    event Approval( address indexed owner, address indexed spender, uint value);
      |}
      |
      |contract DSTokenBase is ERC20, DSMath {
      |    uint256                                            _supply;
      |    mapping (address => uint256)                       _balances;
      |    mapping (address => mapping (address => uint256))  _approvals;
      |
      |    function DSTokenBase(uint256 supply) {
      |        _balances[msg.sender] = supply;
      |        _supply = supply;
      |    }
      |
      |    function totalSupply() constant returns (uint256) {
      |        return _supply;
      |    }
      |    function balanceOf(address src) constant returns (uint256) {
      |        return _balances[src];
      |    }
      |    function allowance(address src, address guy) constant returns (uint256) {
      |        return _approvals[src][guy];
      |    }
      |
      |    function transfer(address dst, uint wad) returns (bool) {
      |        assert(_balances[msg.sender] >= wad);
      |
      |        _balances[msg.sender] = sub(_balances[msg.sender], wad);
      |        _balances[dst] = add(_balances[dst], wad);
      |
      |        Transfer(msg.sender, dst, wad);
      |
      |        return true;
      |    }
      |
      |    function transferFrom(address src, address dst, uint wad) returns (bool) {
      |        assert(_balances[src] >= wad);
      |        assert(_approvals[src][msg.sender] >= wad);
      |
      |        _approvals[src][msg.sender] = sub(_approvals[src][msg.sender], wad);
      |        _balances[src] = sub(_balances[src], wad);
      |        _balances[dst] = add(_balances[dst], wad);
      |
      |        Transfer(src, dst, wad);
      |
      |        return true;
      |    }
      |
      |    function approve(address guy, uint256 wad) returns (bool) {
      |        _approvals[msg.sender][guy] = wad;
      |
      |        Approval(msg.sender, guy, wad);
      |
      |        return true;
      |    }
      |
      |}
      |
      |contract DSToken is DSTokenBase(0), DSStop {
      |
      |    bytes32  public  symbol;
      |    uint256  public  decimals = 18; // standard token precision. override to customize
      |
      |    function DSToken(bytes32 symbol_) {
      |        symbol = symbol_;
      |    }
      |
      |    function transfer(address dst, uint wad) stoppable note returns (bool) {
      |        return super.transfer(dst, wad);
      |    }
      |    function transferFrom(
      |        address src, address dst, uint wad
      |    ) stoppable note returns (bool) {
      |        return super.transferFrom(src, dst, wad);
      |    }
      |    function approve(address guy, uint wad) stoppable note returns (bool) {
      |        return super.approve(guy, wad);
      |    }
      |
      |    function push(address dst, uint128 wad) returns (bool) {
      |        return transfer(dst, wad);
      |    }
      |    function pull(address src, uint128 wad) returns (bool) {
      |        return transferFrom(src, msg.sender, wad);
      |    }
      |
      |    function mint(uint128 wad) auth stoppable note {
      |        _balances[msg.sender] = add(_balances[msg.sender], wad);
      |        _supply = add(_supply, wad);
      |    }
      |    function burn(uint128 wad) auth stoppable note {
      |        _balances[msg.sender] = sub(_balances[msg.sender], wad);
      |        _supply = sub(_supply, wad);
      |    }
      |
      |    // Optional token name
      |
      |    bytes32   public  name = "";
      |
      |    function setName(bytes32 name_) auth {
      |        name = name_;
      |    }
      |
      |}
    """.stripMargin

  val code3 =
    """// some annotations
      |pragma solidity 0.4.24;
      |
      |/**
      | * @title ERC165
      | * @dev https://github.com/ethereum/EIPs/blob/master/EIPS/eip-165.md
      | */
      |interface ERC165 {
      |
      |  /**
      |   * @notice Query if a contract implements an interface
      |   * @param _interfaceId The interface identifier, as specified in ERC-165
      |   * @dev Interface identification is specified in ERC-165. This function
      |   * uses less than 30,000 gas.
      |   */
      |  function supportsInterface(bytes4 _interfaceId) external view returns (bool);
      |
      |}
      |
      |contract ERC721Basic is ERC165 {
      |
      |    event Transfer(
      |        address indexed _from,
      |        address indexed _to,
      |        uint256 indexed _tokenId
      |    );
      |    event Approval(
      |        address indexed _owner,
      |        address indexed _approved,
      |        uint256 indexed _tokenId
      |    );
      |    event ApprovalForAll(
      |        address indexed _owner,
      |        address indexed _operator,
      |        bool _approved
      |    );
      |
      |    function balanceOf(address _owner) public view returns (uint256 _balance);
      |    function ownerOf(uint256 _tokenId) public view returns (address _owner);
      |    function exists(uint256 _tokenId) public view returns (bool _exists);
      |
      |    function approve(address _to, uint256 _tokenId) public;
      |    function getApproved(uint256 _tokenId)
      |        public view returns (address _operator);
      |
      |    function setApprovalForAll(address _operator, bool _approved) public;
      |    function isApprovedForAll(address _owner, address _operator)
      |        public view returns (bool);
      |
      |    function transferFrom(address _from, address _to, uint256 _tokenId) public;
      |    function safeTransferFrom(address _from, address _to, uint256 _tokenId)
      |        public;
      |
      |    function safeTransferFrom(
      |        address _from,
      |        address _to,
      |        uint256 _tokenId,
      |        bytes _data
      |    )
      |        public;
      |}
      |
      |
      |/**
      | * @title SupportsInterfaceWithLookup
      | * @author Matt Condon (@shrugs)
      | * @dev Implements ERC165 using a lookup table.
      | */
      |contract SupportsInterfaceWithLookup is ERC165 {
      |    bytes4 public constant InterfaceId_ERC165 = 0x01ffc9a7;
      |    /**
      |    * 0x01ffc9a7 ===
      |    *   bytes4(keccak256('supportsInterface(bytes4)'))
      |    */
      |
      |    /**
      |    * @dev a mapping of interface id to whether or not it's supported
      |    */
      |    mapping(bytes4 => bool) internal supportedInterfaces;
      |
      |    /**
      |    * @dev A contract implementing SupportsInterfaceWithLookup
      |    * implement ERC165 itself
      |    */
      |    constructor() public {
      |        _registerInterface(InterfaceId_ERC165);
      |    }
      |
      |    /**
      |    * @dev implement supportsInterface(bytes4) using a lookup table
      |    */
      |    function supportsInterface(bytes4 _interfaceId) external view returns (bool) {
      |        return supportedInterfaces[_interfaceId];
      |    }
      |
      |    /**
      |    * @dev private method for registering an interface
      |    */
      |    function _registerInterface(bytes4 _interfaceId) internal {
      |        require(_interfaceId != 0xffffffff);
      |        supportedInterfaces[_interfaceId] = true;
      |    }
      |}
      |
      |contract Governable {
      |
      |    event Pause();
      |    event Unpause();
      |
      |    address public governor;
      |    bool public paused = false;
      |
      |    constructor() public {
      |        governor = msg.sender;
      |    }
      |
      |    function setGovernor(address _gov) public onlyGovernor {
      |        governor = _gov;
      |    }
      |
      |    modifier onlyGovernor {
      |        require(msg.sender == governor);
      |        _;
      |    }
      |
      |    /**
      |    * @dev Modifier to make a function callable only when the contract is not paused.
      |    */
      |    modifier whenNotPaused() {
      |        require(!paused);
      |        _;
      |    }
      |
      |    /**
      |    * @dev Modifier to make a function callable only when the contract is paused.
      |    */
      |    modifier whenPaused() {
      |        require(paused);
      |        _;
      |    }
      |
      |    /**
      |    * @dev called by the owner to pause, triggers stopped state
      |    */
      |    function pause() onlyGovernor whenNotPaused public {
      |        paused = true;
      |        emit Pause();
      |    }
      |
      |    /**
      |    * @dev called by the owner to unpause, returns to normal state
      |    */
      |    function unpause() onlyGovernor whenPaused public {
      |        paused = false;
      |        emit Unpause();
      |    }
      |
      |}
      |
      |contract CardBase is Governable {
      |
      |    struct Card {
      |        uint16 proto;
      |        uint16 purity;
      |    }
      |
      |    function getCard(uint id) public view returns (uint16 proto, uint16 purity) {
      |        Card memory card = cards[id];
      |        return (card.proto, card.purity);
      |    }
      |
      |    function getShine(uint16 purity) public pure returns (uint8) {
      |        return uint8(purity / 1000);
      |    }
      |
      |    Card[] public cards;
      |
      |}
      |
      |contract CardProto is CardBase {
      |
      |    event NewProtoCard(
      |        uint16 id, uint8 season, uint8 god,
      |        Rarity rarity, uint8 mana, uint8 attack,
      |        uint8 health, uint8 cardType, uint8 tribe, bool packable
      |    );
      |
      |    struct Limit {
      |        uint64 limit;
      |        bool exists;
      |    }
      |
      |    // limits for mythic cards
      |    mapping(uint16 => Limit) public limits;
      |
      |    // can only set limits once
      |    function setLimit(uint16 id, uint64 limit) public onlyGovernor {
      |        Limit memory l = limits[id];
      |        require(!l.exists);
      |        limits[id] = Limit({
      |            limit: limit,
      |            exists: true
      |        });
      |    }
      |
      |    function getLimit(uint16 id) public view returns (uint64 limit, bool set) {
      |        Limit memory l = limits[id];
      |        return (l.limit, l.exists);
      |    }
      |
      |    // could make these arrays to save gas
      |    // not really necessary - will be update a very limited no of times
      |    mapping(uint8 => bool) public seasonTradable;
      |    mapping(uint8 => bool) public seasonTradabilityLocked;
      |    uint8 public currentSeason;
      |
      |    function makeTradable(uint8 season) public onlyGovernor {
      |        seasonTradable[season] = true;
      |    }
      |
      |    function makeUntradable(uint8 season) public onlyGovernor {
      |        require(!seasonTradabilityLocked[season]);
      |        seasonTradable[season] = false;
      |    }
      |
      |    function makePermanantlyTradable(uint8 season) public onlyGovernor {
      |        require(seasonTradable[season]);
      |        seasonTradabilityLocked[season] = true;
      |    }
      |
      |    function isTradable(uint16 proto) public view returns (bool) {
      |        return seasonTradable[protos[proto].season];
      |    }
      |
      |    function nextSeason() public onlyGovernor {
      |        //Seasons shouldn't go to 0 if there is more than the uint8 should hold, the governor should know this ¯\_(ツ)_/¯ -M
      |        require(currentSeason <= 255);
      |
      |        currentSeason++;
      |        mythic.length = 0;
      |        legendary.length = 0;
      |        epic.length = 0;
      |        rare.length = 0;
      |        common.length = 0;
      |    }
      |
      |    enum Rarity {
      |        Common,
      |        Rare,
      |        Epic,
      |        Legendary,
      |        Mythic
      |    }
      |
      |    uint8 constant SPELL = 1;
      |    uint8 constant MINION = 2;
      |    uint8 constant WEAPON = 3;
      |    uint8 constant HERO = 4;
      |
      |    struct ProtoCard {
      |        bool exists;
      |        uint8 god;
      |        uint8 season;
      |        uint8 cardType;
      |        Rarity rarity;
      |        uint8 mana;
      |        uint8 attack;
      |        uint8 health;
      |        uint8 tribe;
      |    }
      |
      |    // there is a particular design decision driving this:
      |    // need to be able to iterate over mythics only for card generation
      |    // don't store 5 different arrays: have to use 2 ids
      |    // better to bear this cost (2 bytes per proto card)
      |    // rather than 1 byte per instance
      |
      |    uint16 public protoCount;
      |
      |    mapping(uint16 => ProtoCard) protos;
      |
      |    uint16[] public mythic;
      |    uint16[] public legendary;
      |    uint16[] public epic;
      |    uint16[] public rare;
      |    uint16[] public common;
      |
      |    function addProtos(
      |        uint16[] externalIDs, uint8[] gods, Rarity[] rarities, uint8[] manas, uint8[] attacks,
      |        uint8[] healths, uint8[] cardTypes, uint8[] tribes, bool[] packable
      |    ) public onlyGovernor returns(uint16) {
      |
      |        for (uint i = 0; i < externalIDs.length; i++) {
      |
      |            ProtoCard memory card = ProtoCard({
      |                exists: true,
      |                god: gods[i],
      |                season: currentSeason,
      |                cardType: cardTypes[i],
      |                rarity: rarities[i],
      |                mana: manas[i],
      |                attack: attacks[i],
      |                health: healths[i],
      |                tribe: tribes[i]
      |            });
      |
      |            _addProto(externalIDs[i], card, packable[i]);
      |        }
      |
      |    }
      |
      |    function addProto(
      |        uint16 externalID, uint8 god, Rarity rarity, uint8 mana, uint8 attack, uint8 health, uint8 cardType, uint8 tribe, bool packable
      |    ) public onlyGovernor returns(uint16) {
      |        ProtoCard memory card = ProtoCard({
      |            exists: true,
      |            god: god,
      |            season: currentSeason,
      |            cardType: cardType,
      |            rarity: rarity,
      |            mana: mana,
      |            attack: attack,
      |            health: health,
      |            tribe: tribe
      |        });
      |
      |        _addProto(externalID, card, packable);
      |    }
      |
      |    function addWeapon(
      |        uint16 externalID, uint8 god, Rarity rarity, uint8 mana, uint8 attack, uint8 durability, bool packable
      |    ) public onlyGovernor returns(uint16) {
      |
      |        ProtoCard memory card = ProtoCard({
      |            exists: true,
      |            god: god,
      |            season: currentSeason,
      |            cardType: WEAPON,
      |            rarity: rarity,
      |            mana: mana,
      |            attack: attack,
      |            health: durability,
      |            tribe: 0
      |        });
      |
      |        _addProto(externalID, card, packable);
      |    }
      |
      |    function addSpell(uint16 externalID, uint8 god, Rarity rarity, uint8 mana, bool packable) public onlyGovernor returns(uint16) {
      |
      |        ProtoCard memory card = ProtoCard({
      |            exists: true,
      |            god: god,
      |            season: currentSeason,
      |            cardType: SPELL,
      |            rarity: rarity,
      |            mana: mana,
      |            attack: 0,
      |            health: 0,
      |            tribe: 0
      |        });
      |
      |        _addProto(externalID, card, packable);
      |    }
      |
      |    function addMinion(
      |        uint16 externalID, uint8 god, Rarity rarity, uint8 mana, uint8 attack, uint8 health, uint8 tribe, bool packable
      |    ) public onlyGovernor returns(uint16) {
      |
      |        ProtoCard memory card = ProtoCard({
      |            exists: true,
      |            god: god,
      |            season: currentSeason,
      |            cardType: MINION,
      |            rarity: rarity,
      |            mana: mana,
      |            attack: attack,
      |            health: health,
      |            tribe: tribe
      |        });
      |
      |        _addProto(externalID, card, packable);
      |    }
      |
      |    function _addProto(uint16 externalID, ProtoCard memory card, bool packable) internal {
      |
      |        require(!protos[externalID].exists);
      |
      |        card.exists = true;
      |
      |        protos[externalID] = card;
      |
      |        protoCount++;
      |
      |        emit NewProtoCard(
      |            externalID, currentSeason, card.god,
      |            card.rarity, card.mana, card.attack,
      |            card.health, card.cardType, card.tribe, packable
      |        );
      |
      |        if (packable) {
      |            Rarity rarity = card.rarity;
      |            if (rarity == Rarity.Common) {
      |                common.push(externalID);
      |            } else if (rarity == Rarity.Rare) {
      |                rare.push(externalID);
      |            } else if (rarity == Rarity.Epic) {
      |                epic.push(externalID);
      |            } else if (rarity == Rarity.Legendary) {
      |                legendary.push(externalID);
      |            } else if (rarity == Rarity.Mythic) {
      |                mythic.push(externalID);
      |            } else {
      |                require(false);
      |            }
      |        }
      |    }
      |
      |    function getProto(uint16 id) public view returns(
      |        bool exists, uint8 god, uint8 season, uint8 cardType, Rarity rarity, uint8 mana, uint8 attack, uint8 health, uint8 tribe
      |    ) {
      |        ProtoCard memory proto = protos[id];
      |        return (
      |            proto.exists,
      |            proto.god,
      |            proto.season,
      |            proto.cardType,
      |            proto.rarity,
      |            proto.mana,
      |            proto.attack,
      |            proto.health,
      |            proto.tribe
      |        );
      |    }
      |
      |    function getRandomCard(Rarity rarity, uint16 random) public view returns (uint16) {
      |        // modulo bias is fine - creates rarity tiers etc
      |        // will obviously revert is there are no cards of that type: this is expected - should never happen
      |        if (rarity == Rarity.Common) {
      |            return common[random % common.length];
      |        } else if (rarity == Rarity.Rare) {
      |            return rare[random % rare.length];
      |        } else if (rarity == Rarity.Epic) {
      |            return epic[random % epic.length];
      |        } else if (rarity == Rarity.Legendary) {
      |            return legendary[random % legendary.length];
      |        } else if (rarity == Rarity.Mythic) {
      |            // make sure a mythic is available
      |            uint16 id;
      |            uint64 limit;
      |            bool set;
      |            for (uint i = 0; i < mythic.length; i++) {
      |                id = mythic[(random + i) % mythic.length];
      |                (limit, set) = getLimit(id);
      |                if (set && limit > 0){
      |                    return id;
      |                }
      |            }
      |            // if not, they get a legendary :(
      |            return legendary[random % legendary.length];
      |        }
      |        require(false);
      |        return 0;
      |    }
      |
      |    // can never adjust tradable cards
      |    // each season gets a 'balancing beta'
      |    // totally immutable: season, rarity
      |    function replaceProto(
      |        uint16 index, uint8 god, uint8 cardType, uint8 mana, uint8 attack, uint8 health, uint8 tribe
      |    ) public onlyGovernor {
      |        ProtoCard memory pc = protos[index];
      |        require(!seasonTradable[pc.season]);
      |        protos[index] = ProtoCard({
      |            exists: true,
      |            god: god,
      |            season: pc.season,
      |            cardType: cardType,
      |            rarity: pc.rarity,
      |            mana: mana,
      |            attack: attack,
      |            health: health,
      |            tribe: tribe
      |        });
      |    }
      |
      |}
      |
      |contract ERC721Receiver {
      |    /**
      |    * @dev Magic value to be returned upon successful reception of an NFT
      |    *  Equals to `bytes4(keccak256("onERC721Received(address,address,uint256,bytes)"))`,
      |    *  which can be also obtained as `ERC721Receiver(0).onERC721Received.selector`
      |    */
      |    bytes4 internal constant ERC721_RECEIVED = 0x150b7a02;
      |
      |    /**
      |    * @notice Handle the receipt of an NFT
      |    * @dev The ERC721 smart contract calls this function on the recipient
      |    * after a `safetransfer`. This function MAY throw to revert and reject the
      |    * transfer. Return of other than the magic value MUST result in the
      |    * transaction being reverted.
      |    * Note: the contract address is always the message sender.
      |    * @param _operator The address which called `safeTransferFrom` function
      |    * @param _from The address which previously owned the token
      |    * @param _tokenId The NFT identifier which is being transfered
      |    * @param _data Additional data with no specified format
      |    * @return `bytes4(keccak256("onERC721Received(address,address,uint256,bytes)"))`
      |    */
      |    function onERC721Received(
      |        address _operator,
      |        address _from,
      |        uint256 _tokenId,
      |        bytes _data
      |    )
      |        public
      |        returns(bytes4);
      |}
      |
      |library AddressUtils {
      |
      |  /**
      |   * Returns whether the target address is a contract
      |   * @dev This function will return false if invoked during the constructor of a contract,
      |   * as the code is not actually created until after the constructor finishes.
      |   * @param addr address to check
      |   * @return whether the target address is a contract
      |   */
      |    function isContract(address addr) internal view returns (bool) {
      |        uint256 size;
      |        // XXX Currently there is no better way to check if there is a contract in an address
      |        // than to check the size of the code at that address.
      |        // See https://ethereum.stackexchange.com/a/14016/36603
      |        // for more details about how this works.
      |        // TODO Check this again before the Serenity release, because all addresses will be
      |        // contracts then.
      |        // solium-disable-next-line security/no-inline-assembly
      |        assembly { size := extcodesize(addr) }
      |        return size > 0;
      |    }
      |
      |}
      |
      |library SafeMath {
      |
      |  /**
      |  * @dev Multiplies two numbers, throws on overflow.
      |  */
      |  function mul(uint256 a, uint256 b) internal pure returns (uint256 c) {
      |    // Gas optimization: this is cheaper than asserting 'a' not being zero, but the
      |    // benefit is lost if 'b' is also tested.
      |    // See: https://github.com/OpenZeppelin/openzeppelin-solidity/pull/522
      |    if (a == 0) {
      |      return 0;
      |    }
      |
      |    c = a * b;
      |    assert(c / a == b);
      |    return c;
      |  }
      |
      |  /**
      |  * @dev Integer division of two numbers, truncating the quotient.
      |  */
      |  function div(uint256 a, uint256 b) internal pure returns (uint256) {
      |    // assert(b > 0); // Solidity automatically throws when dividing by 0
      |    // uint256 c = a / b;
      |    // assert(a == b * c + a % b); // There is no case in which this doesn't hold
      |    return a / b;
      |  }
      |
      |  /**
      |  * @dev Subtracts two numbers, throws on overflow (i.e. if subtrahend is greater than minuend).
      |  */
      |  function sub(uint256 a, uint256 b) internal pure returns (uint256) {
      |    assert(b <= a);
      |    return a - b;
      |  }
      |
      |  /**
      |  * @dev Adds two numbers, throws on overflow.
      |  */
      |  function add(uint256 a, uint256 b) internal pure returns (uint256 c) {
      |    c = a + b;
      |    assert(c >= a);
      |    return c;
      |  }
      |}
      |
      |contract ERC721BasicToken is CardProto, SupportsInterfaceWithLookup, ERC721Basic {
      |
      |    bytes4 private constant InterfaceId_ERC721 = 0x80ac58cd;
      |    /*
      |    * 0x80ac58cd ===
      |    *   bytes4(keccak256('balanceOf(address)')) ^
      |    *   bytes4(keccak256('ownerOf(uint256)')) ^
      |    *   bytes4(keccak256('approve(address,uint256)')) ^
      |    *   bytes4(keccak256('getApproved(uint256)')) ^
      |    *   bytes4(keccak256('setApprovalForAll(address,bool)')) ^
      |    *   bytes4(keccak256('isApprovedForAll(address,address)')) ^
      |    *   bytes4(keccak256('transferFrom(address,address,uint256)')) ^
      |    *   bytes4(keccak256('safeTransferFrom(address,address,uint256)')) ^
      |    *   bytes4(keccak256('safeTransferFrom(address,address,uint256,bytes)'))
      |    */
      |
      |    bytes4 private constant InterfaceId_ERC721Exists = 0x4f558e79;
      |    /*
      |    * 0x4f558e79 ===
      |    *   bytes4(keccak256('exists(uint256)'))
      |    */
      |
      |    using SafeMath for uint256;
      |    using AddressUtils for address;
      |
      |    // Equals to `bytes4(keccak256("onERC721Received(address,address,uint256,bytes)"))`
      |    // which can be also obtained as `ERC721Receiver(0).onERC721Received.selector`
      |    bytes4 private constant ERC721_RECEIVED = 0x150b7a02;
      |
      |    // Mapping from token ID to owner
      |    mapping (uint256 => address) internal tokenOwner;
      |
      |    // Mapping from token ID to approved address
      |    mapping (uint256 => address) internal tokenApprovals;
      |
      |    // Mapping from owner to number of owned token
      |    // mapping (address => uint256) internal ownedTokensCount;
      |
      |    // Mapping from owner to operator approvals
      |    mapping (address => mapping (address => bool)) internal operatorApprovals;
      |
      |    /**
      |    * @dev Guarantees msg.sender is owner of the given token
      |    * @param _tokenId uint256 ID of the token to validate its ownership belongs to msg.sender
      |    */
      |    modifier onlyOwnerOf(uint256 _tokenId) {
      |        require(ownerOf(_tokenId) == msg.sender);
      |        _;
      |    }
      |
      |    /**
      |    * @dev Checks msg.sender can transfer a token, by being owner, approved, or operator
      |    * @param _tokenId uint256 ID of the token to validate
      |    */
      |    modifier canTransfer(uint256 _tokenId) {
      |        require(isApprovedOrOwner(msg.sender, _tokenId));
      |        _;
      |    }
      |
      |    constructor()
      |        public
      |    {
      |        // register the supported interfaces to conform to ERC721 via ERC165
      |        _registerInterface(InterfaceId_ERC721);
      |        _registerInterface(InterfaceId_ERC721Exists);
      |    }
      |
      |    /**
      |    * @dev Gets the balance of the specified address
      |    * @param _owner address to query the balance of
      |    * @return uint256 representing the amount owned by the passed address
      |    */
      |    function balanceOf(address _owner) public view returns (uint256);
      |
      |    /**
      |    * @dev Gets the owner of the specified token ID
      |    * @param _tokenId uint256 ID of the token to query the owner of
      |    * @return owner address currently marked as the owner of the given token ID
      |    */
      |    function ownerOf(uint256 _tokenId) public view returns (address) {
      |        address owner = tokenOwner[_tokenId];
      |        require(owner != address(0));
      |        return owner;
      |    }
      |
      |    /**
      |    * @dev Returns whether the specified token exists
      |    * @param _tokenId uint256 ID of the token to query the existence of
      |    * @return whether the token exists
      |    */
      |    function exists(uint256 _tokenId) public view returns (bool) {
      |        address owner = tokenOwner[_tokenId];
      |        return owner != address(0);
      |    }
      |
      |    /**
      |    * @dev Approves another address to transfer the given token ID
      |    * The zero address indicates there is no approved address.
      |    * There can only be one approved address per token at a given time.
      |    * Can only be called by the token owner or an approved operator.
      |    * @param _to address to be approved for the given token ID
      |    * @param _tokenId uint256 ID of the token to be approved
      |    */
      |    function approve(address _to, uint256 _tokenId) public {
      |        address owner = ownerOf(_tokenId);
      |        require(_to != owner);
      |        require(msg.sender == owner || isApprovedForAll(owner, msg.sender));
      |
      |        tokenApprovals[_tokenId] = _to;
      |        emit Approval(owner, _to, _tokenId);
      |    }
      |
      |    /**
      |    * @dev Gets the approved address for a token ID, or zero if no address set
      |    * @param _tokenId uint256 ID of the token to query the approval of
      |    * @return address currently approved for the given token ID
      |    */
      |    function getApproved(uint256 _tokenId) public view returns (address) {
      |        return tokenApprovals[_tokenId];
      |    }
      |
      |    /**
      |    * @dev Sets or unsets the approval of a given operator
      |    * An operator is allowed to transfer all tokens of the sender on their behalf
      |    * @param _to operator address to set the approval
      |    * @param _approved representing the status of the approval to be set
      |    */
      |    function setApprovalForAll(address _to, bool _approved) public {
      |        require(_to != msg.sender);
      |        operatorApprovals[msg.sender][_to] = _approved;
      |        emit ApprovalForAll(msg.sender, _to, _approved);
      |    }
      |
      |    /**
      |    * @dev Tells whether an operator is approved by a given owner
      |    * @param _owner owner address which you want to query the approval of
      |    * @param _operator operator address which you want to query the approval of
      |    * @return bool whether the given operator is approved by the given owner
      |    */
      |    function isApprovedForAll(
      |        address _owner,
      |        address _operator
      |    )
      |        public
      |        view
      |        returns (bool)
      |    {
      |        return operatorApprovals[_owner][_operator];
      |    }
      |
      |    /**
      |    * @dev Transfers the ownership of a given token ID to another address
      |    * Usage of this method is discouraged, use `safeTransferFrom` whenever possible
      |    * Requires the msg sender to be the owner, approved, or operator
      |    * @param _from current owner of the token
      |    * @param _to address to receive the ownership of the given token ID
      |    * @param _tokenId uint256 ID of the token to be transferred
      |    */
      |    function transferFrom(
      |        address _from,
      |        address _to,
      |        uint256 _tokenId
      |    )
      |        public
      |        canTransfer(_tokenId)
      |    {
      |        require(_from != address(0));
      |        require(_to != address(0));
      |
      |        clearApproval(_from, _tokenId);
      |        removeTokenFrom(_from, _tokenId);
      |        addTokenTo(_to, _tokenId);
      |
      |        emit Transfer(_from, _to, _tokenId);
      |    }
      |
      |    /**
      |    * @dev Safely transfers the ownership of a given token ID to another address
      |    * If the target address is a contract, it must implement `onERC721Received`,
      |    * which is called upon a safe transfer, and return the magic value
      |    * `bytes4(keccak256("onERC721Received(address,address,uint256,bytes)"))`; otherwise,
      |    * the transfer is reverted.
      |    *
      |    * Requires the msg sender to be the owner, approved, or operator
      |    * @param _from current owner of the token
      |    * @param _to address to receive the ownership of the given token ID
      |    * @param _tokenId uint256 ID of the token to be transferred
      |    */
      |    function safeTransferFrom(
      |        address _from,
      |        address _to,
      |        uint256 _tokenId
      |    )
      |        public
      |        canTransfer(_tokenId)
      |    {
      |        // solium-disable-next-line arg-overflow
      |        safeTransferFrom(_from, _to, _tokenId, "");
      |    }
      |
      |    /**
      |    * @dev Safely transfers the ownership of a given token ID to another address
      |    * If the target address is a contract, it must implement `onERC721Received`,
      |    * which is called upon a safe transfer, and return the magic value
      |    * `bytes4(keccak256("onERC721Received(address,address,uint256,bytes)"))`; otherwise,
      |    * the transfer is reverted.
      |    * Requires the msg sender to be the owner, approved, or operator
      |    * @param _from current owner of the token
      |    * @param _to address to receive the ownership of the given token ID
      |    * @param _tokenId uint256 ID of the token to be transferred
      |    * @param _data bytes data to send along with a safe transfer check
      |    */
      |    function safeTransferFrom(
      |        address _from,
      |        address _to,
      |        uint256 _tokenId,
      |        bytes _data
      |    )
      |        public
      |        canTransfer(_tokenId)
      |    {
      |        transferFrom(_from, _to, _tokenId);
      |        // solium-disable-next-line arg-overflow
      |        require(checkAndCallSafeTransfer(_from, _to, _tokenId, _data));
      |    }
      |
      |    /**
      |    * @dev Returns whether the given spender can transfer a given token ID
      |    * @param _spender address of the spender to query
      |    * @param _tokenId uint256 ID of the token to be transferred
      |    * @return bool whether the msg.sender is approved for the given token ID,
      |    *  is an operator of the owner, or is the owner of the token
      |    */
      |    function isApprovedOrOwner(
      |        address _spender,
      |        uint256 _tokenId
      |    )
      |        internal
      |        view
      |        returns (bool)
      |    {
      |        address owner = ownerOf(_tokenId);
      |        // Disable solium check because of
      |        // https://github.com/duaraghav8/Solium/issues/175
      |        // solium-disable-next-line operator-whitespace
      |        return (
      |        _spender == owner ||
      |        getApproved(_tokenId) == _spender ||
      |        isApprovedForAll(owner, _spender)
      |        );
      |    }
      |
      |    /**
      |    * @dev Internal function to clear current approval of a given token ID
      |    * Reverts if the given address is not indeed the owner of the token
      |    * @param _owner owner of the token
      |    * @param _tokenId uint256 ID of the token to be transferred
      |    */
      |    function clearApproval(address _owner, uint256 _tokenId) internal {
      |        require(ownerOf(_tokenId) == _owner);
      |        if (tokenApprovals[_tokenId] != address(0)) {
      |            tokenApprovals[_tokenId] = address(0);
      |        }
      |    }
      |
      |    /**
      |    * @dev Internal function to mint a new token
      |    * Reverts if the given token ID already exists
      |    * @param _to The address that will own the minted token
      |    * @param _tokenId uint256 ID of the token to be minted by the msg.sender
      |    */
      |    function _mint(address _to, uint256 _tokenId) internal {
      |        require(_to != address(0));
      |        addNewTokenTo(_to, _tokenId);
      |        emit Transfer(address(0), _to, _tokenId);
      |    }
      |
      |
      |    /**
      |    * @dev Internal function to burn a specific token
      |    * Reverts if the token does not exist
      |    * @param _tokenId uint256 ID of the token being burned by the msg.sender
      |    */
      |    function _burn(address _owner, uint256 _tokenId) internal {
      |        clearApproval(_owner, _tokenId);
      |        removeTokenFrom(_owner, _tokenId);
      |        emit Transfer(_owner, address(0), _tokenId);
      |    }
      |
      |    function addNewTokenTo(address _to, uint256 _tokenId) internal {
      |        require(tokenOwner[_tokenId] == address(0));
      |        tokenOwner[_tokenId] = _to;
      |    }
      |
      |    /**
      |    * @dev Internal function to add a token ID to the list of a given address
      |    * @param _to address representing the new owner of the given token ID
      |    * @param _tokenId uint256 ID of the token to be added to the tokens list of the given address
      |    */
      |    function addTokenTo(address _to, uint256 _tokenId) internal {
      |        require(tokenOwner[_tokenId] == address(0));
      |        tokenOwner[_tokenId] = _to;
      |        // ownedTokensCount[_to] = ownedTokensCount[_to].add(1);
      |    }
      |
      |    /**
      |    * @dev Internal function to remove a token ID from the list of a given address
      |    * @param _from address representing the previous owner of the given token ID
      |    * @param _tokenId uint256 ID of the token to be removed from the tokens list of the given address
      |    */
      |    function removeTokenFrom(address _from, uint256 _tokenId) internal {
      |        require(ownerOf(_tokenId) == _from);
      |        // ownedTokensCount[_from] = ownedTokensCount[_from].sub(1);
      |        tokenOwner[_tokenId] = address(0);
      |    }
      |
      |    /**
      |    * @dev Internal function to invoke `onERC721Received` on a target address
      |    * The call is not executed if the target address is not a contract
      |    * @param _from address representing the previous owner of the given token ID
      |    * @param _to target address that will receive the tokens
      |    * @param _tokenId uint256 ID of the token to be transferred
      |    * @param _data bytes optional data to send along with the call
      |    * @return whether the call correctly returned the expected magic value
      |    */
      |    function checkAndCallSafeTransfer(
      |        address _from,
      |        address _to,
      |        uint256 _tokenId,
      |        bytes _data
      |    )
      |        internal
      |        returns (bool)
      |    {
      |        if (!_to.isContract()) {
      |            return true;
      |        }
      |        bytes4 retval = ERC721Receiver(_to).onERC721Received(
      |        msg.sender, _from, _tokenId, _data);
      |        return (retval == ERC721_RECEIVED);
      |    }
      |
      |}
      |
      |
      |
      |contract ERC721Enumerable is ERC721Basic {
      |    function totalSupply() public view returns (uint256);
      |    function tokenOfOwnerByIndex(
      |        address _owner,
      |        uint256 _index
      |    )
      |        public
      |        view
      |        returns (uint256 _tokenId);
      |
      |    function tokenByIndex(uint256 _index) public view returns (uint256);
      |}
      |
      |contract ERC721Metadata is ERC721Basic {
      |    function name() external view returns (string _name);
      |    function symbol() external view returns (string _symbol);
      |    function tokenURI(uint256 _tokenId) public view returns (string);
      |}
      |
      |contract ERC721 is ERC721Basic, ERC721Enumerable, ERC721Metadata {
      |
      |}
      |
      |
      |
      |
      |library Strings {
      |
      |  // via https://github.com/oraclize/ethereum-api/blob/master/oraclizeAPI_0.5.sol
      |  function strConcat(string _a, string _b, string _c, string _d, string _e) internal pure returns (string) {
      |      bytes memory _ba = bytes(_a);
      |      bytes memory _bb = bytes(_b);
      |      bytes memory _bc = bytes(_c);
      |      bytes memory _bd = bytes(_d);
      |      bytes memory _be = bytes(_e);
      |      string memory abcde = new string(_ba.length + _bb.length + _bc.length + _bd.length + _be.length);
      |      bytes memory babcde = bytes(abcde);
      |      uint k = 0;
      |      for (uint i = 0; i < _ba.length; i++) babcde[k++] = _ba[i];
      |      for (i = 0; i < _bb.length; i++) babcde[k++] = _bb[i];
      |      for (i = 0; i < _bc.length; i++) babcde[k++] = _bc[i];
      |      for (i = 0; i < _bd.length; i++) babcde[k++] = _bd[i];
      |      for (i = 0; i < _be.length; i++) babcde[k++] = _be[i];
      |      return string(babcde);
      |    }
      |
      |    function strConcat(string _a, string _b, string _c, string _d) internal pure returns (string) {
      |        return strConcat(_a, _b, _c, _d, "");
      |    }
      |
      |    function strConcat(string _a, string _b, string _c) internal pure returns (string) {
      |        return strConcat(_a, _b, _c, "", "");
      |    }
      |
      |    function strConcat(string _a, string _b) internal pure returns (string) {
      |        return strConcat(_a, _b, "", "", "");
      |    }
      |
      |    function uint2str(uint i) internal pure returns (string) {
      |        if (i == 0) return "0";
      |        uint j = i;
      |        uint len;
      |        while (j != 0){
      |            len++;
      |            j /= 10;
      |        }
      |        bytes memory bstr = new bytes(len);
      |        uint k = len - 1;
      |        while (i != 0){
      |            bstr[k--] = byte(48 + i % 10);
      |            i /= 10;
      |        }
      |        return string(bstr);
      |    }
      |}
      |
      |contract ERC721Token is SupportsInterfaceWithLookup, ERC721BasicToken, ERC721 {
      |
      |    using Strings for string;
      |
      |    bytes4 private constant InterfaceId_ERC721Enumerable = 0x780e9d63;
      |    /**
      |    * 0x780e9d63 ===
      |    *   bytes4(keccak256('totalSupply()')) ^
      |    *   bytes4(keccak256('tokenOfOwnerByIndex(address,uint256)')) ^
      |    *   bytes4(keccak256('tokenByIndex(uint256)'))
      |    */
      |
      |    bytes4 private constant InterfaceId_ERC721Metadata = 0x5b5e139f;
      |    /**
      |    * 0x5b5e139f ===
      |    *   bytes4(keccak256('name()')) ^
      |    *   bytes4(keccak256('symbol()')) ^
      |    *   bytes4(keccak256('tokenURI(uint256)'))
      |    */
      |
      |    /*** Constants ***/
      |    // Configure these for your own deployment
      |    string public constant NAME = "Gods Unchained";
      |    string public constant SYMBOL = "GODS";
      |    string public tokenMetadataBaseURI = "https://api.godsunchained.com/card/";
      |
      |    // Mapping from owner to list of owned token IDs
      |    // EDITED: limit to 2^40 (around 1T)
      |    mapping(address => uint40[]) internal ownedTokens;
      |
      |    uint32[] ownedTokensIndex;
      |
      |    /**
      |    * @dev Constructor function
      |    */
      |    constructor() public {
      |
      |        // register the supported interfaces to conform to ERC721 via ERC165
      |        _registerInterface(InterfaceId_ERC721Enumerable);
      |        _registerInterface(InterfaceId_ERC721Metadata);
      |    }
      |
      |    /**
      |    * @dev Gets the token name
      |    * @return string representing the token name
      |    */
      |    function name() external view returns (string) {
      |        return NAME;
      |    }
      |
      |    /**
      |    * @dev Gets the token symbol
      |    * @return string representing the token symbol
      |    */
      |    function symbol() external view returns (string) {
      |        return SYMBOL;
      |    }
      |
      |    /**
      |    * @dev Returns an URI for a given token ID
      |    * Throws if the token ID does not exist. May return an empty string.
      |    * @param _tokenId uint256 ID of the token to query
      |    */
      |    function tokenURI(uint256 _tokenId) public view returns (string) {
      |        return Strings.strConcat(
      |            tokenMetadataBaseURI,
      |            Strings.uint2str(_tokenId)
      |        );
      |    }
      |
      |    /**
      |    * @dev Gets the token ID at a given index of the tokens list of the requested owner
      |    * @param _owner address owning the tokens list to be accessed
      |    * @param _index uint256 representing the index to be accessed of the requested tokens list
      |    * @return uint256 token ID at the given index of the tokens list owned by the requested address
      |    */
      |    function tokenOfOwnerByIndex(
      |        address _owner,
      |        uint256 _index
      |    )
      |        public
      |        view
      |        returns (uint256)
      |    {
      |        require(_index < balanceOf(_owner));
      |        return ownedTokens[_owner][_index];
      |    }
      |
      |    /**
      |    * @dev Gets the total amount of tokens stored by the contract
      |    * @return uint256 representing the total amount of tokens
      |    */
      |    function totalSupply() public view returns (uint256) {
      |        return cards.length;
      |    }
      |
      |    /**
      |    * @dev Gets the token ID at a given index of all the tokens in this contract
      |    * Reverts if the index is greater or equal to the total number of tokens
      |    * @param _index uint256 representing the index to be accessed of the tokens list
      |    * @return uint256 token ID at the given index of the tokens list
      |    */
      |    function tokenByIndex(uint256 _index) public view returns (uint256) {
      |        require(_index < totalSupply());
      |        return _index;
      |    }
      |
      |    /**
      |    * @dev Internal function to add a token ID to the list of a given address
      |    * @param _to address representing the new owner of the given token ID
      |    * @param _tokenId uint256 ID of the token to be added to the tokens list of the given address
      |    */
      |    function addTokenTo(address _to, uint256 _tokenId) internal {
      |        super.addTokenTo(_to, _tokenId);
      |        uint256 length = ownedTokens[_to].length;
      |        // EDITED: prevent overflow
      |        require(length == uint32(length));
      |        ownedTokens[_to].push(uint40(_tokenId));
      |
      |        ownedTokensIndex[_tokenId] = uint32(length);
      |    }
      |
      |    // EDITED
      |    // have to have in order to use array rather than mapping
      |    function addNewTokenTo(address _to, uint256 _tokenId) internal {
      |        super.addNewTokenTo(_to, _tokenId);
      |        uint256 length = ownedTokens[_to].length;
      |        // EDITED: prevent overflow
      |        require(length == uint32(length));
      |        ownedTokens[_to].push(uint40(_tokenId));
      |        ownedTokensIndex.push(uint32(length));
      |    }
      |
      |    /**
      |    * @dev Internal function to remove a token ID from the list of a given address
      |    * @param _from address representing the previous owner of the given token ID
      |    * @param _tokenId uint256 ID of the token to be removed from the tokens list of the given address
      |    */
      |    function removeTokenFrom(address _from, uint256 _tokenId) internal {
      |        super.removeTokenFrom(_from, _tokenId);
      |
      |        uint32 tokenIndex = ownedTokensIndex[_tokenId];
      |        uint256 lastTokenIndex = ownedTokens[_from].length.sub(1);
      |        uint40 lastToken = ownedTokens[_from][lastTokenIndex];
      |
      |        ownedTokens[_from][tokenIndex] = lastToken;
      |        ownedTokens[_from][lastTokenIndex] = 0;
      |        // Note that this will handle single-element arrays. In that case, both tokenIndex and lastTokenIndex are going to
      |        // be zero. Then we can make sure that we will remove _tokenId from the ownedTokens list since we are first swapping
      |        // the lastToken to the first position, and then dropping the element placed in the last position of the list
      |
      |        ownedTokens[_from].length--;
      |        ownedTokensIndex[_tokenId] = 0;
      |        ownedTokensIndex[lastToken] = tokenIndex;
      |    }
      |
      |    /**
      |    * @dev Gets the balance of the specified address - overrriden from previous to save gas
      |    * @param _owner address to query the balance of
      |    * @return uint256 representing the amount owned by the passed address
      |    */
      |    function balanceOf(address _owner) public view returns (uint256) {
      |        return ownedTokens[_owner].length;
      |    }
      |
      |}
      |
      |contract CardOwnershipTwo is ERC721Token {
      |
      |    uint public burnCount;
      |
      |    function getActiveCards() public view returns (uint) {
      |        return totalSupply() - burnCount;
      |    }
      |
      |    /**
      |    * @param to : the address to which the card will be transferred
      |    * @param id : the id of the card to be transferred
      |    */
      |    function transfer(address to, uint id) public payable onlyOwnerOf(id) {
      |        require(isTradable(cards[id].proto));
      |        require(to != address(0));
      |
      |        _transfer(msg.sender, to, id);
      |    }
      |
      |    function _transfer(address from, address to, uint id) internal {
      |
      |        clearApproval(from, id);
      |
      |        removeTokenFrom(from, id);
      |
      |        addTokenTo(to, id);
      |
      |        emit Transfer(from, to, id);
      |    }
      |
      |    /**
      |    * @param to : the address to which the cards will be transferred
      |    * @param ids : the ids of the cards to be transferred
      |    */
      |    function transferAll(address to, uint[] ids) public payable {
      |        for (uint i = 0; i < ids.length; i++) {
      |            transfer(to, ids[i]);
      |        }
      |    }
      |
      |    /**
      |    * @param proposed : the claimed owner of the cards
      |    * @param ids : the ids of the cards to check
      |    * @return whether proposed owns all of the cards
      |    */
      |    function ownsAll(address proposed, uint[] ids) public view returns (bool) {
      |        require(ids.length > 0);
      |        for (uint i = 0; i < ids.length; i++) {
      |            if (!owns(proposed, ids[i])) {
      |                return false;
      |            }
      |        }
      |        return true;
      |    }
      |
      |    /**
      |    * @param proposed : the claimed owner of the card
      |    * @param id : the id of the card to check
      |    * @return whether proposed owns the card
      |    */
      |    function owns(address proposed, uint id) public view returns (bool) {
      |        return ownerOf(id) == proposed;
      |    }
      |
      |    function burn(uint id) public onlyOwnerOf(id) {
      |        burnCount++;
      |        _burn(msg.sender, id);
      |    }
      |
      |    /**
      |    * @param ids : the indices of the tokens to burn
      |    */
      |    function burnAll(uint[] ids) public {
      |        for (uint i = 0; i < ids.length; i++){
      |            burn(ids[i]);
      |        }
      |    }
      |
      |    /**
      |    * @param to : the address to approve for transfer
      |    * @param id : the index of the card to be approved
      |    */
      |    function approve(address to, uint id) public {
      |        require(isTradable(cards[id].proto));
      |        super.approve(to, id);
      |    }
      |
      |    /**
      |    * @param to : the address to approve for transfer
      |    * @param ids : the indices of the cards to be approved
      |    */
      |    function approveAll(address to, uint[] ids) public {
      |        for (uint i = 0; i < ids.length; i++) {
      |            approve(to, ids[i]);
      |        }
      |    }
      |
      |    /**
      |    * @param to : the address to which the token should be transferred
      |    * @param id : the index of the token to transfer
      |    */
      |    function transferFrom(address from, address to, uint id) public {
      |        require(isTradable(cards[id].proto));
      |        super.transferFrom(from, to, id);
      |    }
      |
      |    /**
      |    * @param to : the address to which the tokens should be transferred
      |    * @param ids : the indices of the tokens to transfer
      |    */
      |    function transferAllFrom(address from, address to, uint[] ids) public {
      |        for (uint i = 0; i < ids.length; i++) {
      |            transferFrom(from, to, ids[i]);
      |        }
      |    }
      |
      |    /**
      |     * @return the number of cards which have been burned
      |     */
      |    function getBurnCount() public view returns (uint) {
      |        return burnCount;
      |    }
      |
      |}
      |
      |contract CardIntegrationTwo is CardOwnershipTwo {
      |
      |    address[] public packs;
      |
      |    event CardCreated(uint indexed id, uint16 proto, uint16 purity, address owner);
      |
      |    function addPack(address approved) public onlyGovernor {
      |        packs.push(approved);
      |    }
      |
      |    modifier onlyApprovedPacks {
      |        require(_isApprovedPack());
      |        _;
      |    }
      |
      |    function _isApprovedPack() private view returns (bool) {
      |        for (uint i = 0; i < packs.length; i++) {
      |            if (msg.sender == address(packs[i])) {
      |                return true;
      |            }
      |        }
      |        return false;
      |    }
      |
      |    function createCard(address owner, uint16 proto, uint16 purity) public whenNotPaused onlyApprovedPacks returns (uint) {
      |        ProtoCard memory card = protos[proto];
      |        require(card.season == currentSeason);
      |        if (card.rarity == Rarity.Mythic) {
      |            uint64 limit;
      |            bool exists;
      |            (limit, exists) = getLimit(proto);
      |            require(!exists || limit > 0);
      |            limits[proto].limit--;
      |        }
      |        return _createCard(owner, proto, purity);
      |    }
      |
      |    function _createCard(address owner, uint16 proto, uint16 purity) internal returns (uint) {
      |        Card memory card = Card({
      |            proto: proto,
      |            purity: purity
      |        });
      |
      |        uint id = cards.push(card) - 1;
      |
      |        _mint(owner, id);
      |
      |        emit CardCreated(id, proto, purity, owner);
      |
      |        return id;
      |    }
      |
      |    /*function combineCards(uint[] ids) public whenNotPaused {
      |        require(ids.length == 5);
      |        require(ownsAll(msg.sender, ids));
      |        Card memory first = cards[ids[0]];
      |        uint16 proto = first.proto;
      |        uint8 shine = _getShine(first.purity);
      |        require(shine < shineLimit);
      |        uint16 puritySum = first.purity - (shine * 1000);
      |        burn(ids[0]);
      |        for (uint i = 1; i < ids.length; i++) {
      |            Card memory next = cards[ids[i]];
      |            require(next.proto == proto);
      |            require(_getShine(next.purity) == shine);
      |            puritySum += (next.purity - (shine * 1000));
      |            burn(ids[i]);
      |        }
      |        uint16 newPurity = uint16(((shine + 1) * 1000) + (puritySum / ids.length));
      |        _createCard(msg.sender, proto, newPurity);
      |    }*/
      |
      |
      |    // PURITY NOTES
      |    // currently, we only
      |    // however, to protect rarity, you'll never be abl
      |    // this is enforced by the restriction in the create-card function
      |    // no cards above this point can be found in packs
      |
      |
      |
      |}
      |
      |contract PreviousInterface {
      |
      |    function ownerOf(uint id) public view returns (address);
      |
      |    function getCard(uint id) public view returns (uint16, uint16);
      |
      |    function totalSupply() public view returns (uint);
      |
      |    function burnCount() public view returns (uint);
      |
      |}
      |
      |contract CardMigration is CardIntegrationTwo {
      |
      |    constructor(PreviousInterface previous) public {
      |        old = previous;
      |    }
      |
      |    // use interface to lower deployment cost
      |    PreviousInterface old;
      |
      |    mapping(uint => bool) public migrated;
      |
      |    function migrate(uint id) public {
      |
      |        require(!migrated[id]);
      |
      |        migrated[id] = true;
      |
      |        address owner = old.ownerOf(id);
      |
      |        uint16 proto;
      |        uint16 purity;
      |
      |        (proto, purity) = old.getCard(id);
      |
      |        _createCard(owner, proto, purity);
      |    }
      |
      |    function migrateAll(uint[] ids) public {
      |
      |        for (uint i = 0; i < ids.length; i++){
      |            migrate(ids[i]);
      |        }
      |
      |    }
      |
      |}
    """.stripMargin

  "SolidityParser" should {
    "parse contract" in {
      val contract = SolidityParser.parseContract(code)
      contract.isSuccess shouldBe true
      contract.get.value.toABI()
    }

    "parse identifier" in {
      val id1 = SolidityParser.parseIdentifier("public")
      id1.isSuccess shouldBe false

      val id2 = SolidityParser.parseIdentifier("public1")
      id2.isSuccess shouldBe true
    }

    "parse modifierInvocation" in {
      val mi1 = SolidityParser.parseModifierInvocation("public")
      mi1.isSuccess shouldBe false

      val mi2 = SolidityParser.parseModifierInvocation("public1")
      mi2.isSuccess shouldBe true
      mi2.get.value shouldBe Invocation("public1")
    }

    "pares modifierList" in {
      val modifierList = SolidityParser.parseModifierList("view onlyBy public public1")
      modifierList.isSuccess shouldBe true
      modifierList.get.value shouldBe ModifierList(Set(Invocation("onlyBy"), Invocation("public1")), Some(Public), Some(View))
    }

    "parse function" in {
      val code =
        """function burnFrom(bytes32 _from, address _value) view onlyBy public public1 returns (bool success) {
          |    require(balanceOf[_from] >= _value);                // Check if the targeted balance is enough
          |    require(_value <= allowance[_from][msg.sender]);    // Check allowance
          |    balanceOf[_from] -= _value;                         // Subtract from the targeted balance
          |    allowance[_from][msg.sender] -= _value;             // Subtract from the sender's allowance
          |    totalSupply -= _value;                              // Update totalSupply
          |    Burn(_from, _value);
          |    emit Burn(_from, _value);
          |    return true;
          |}
        """.stripMargin

      val func = SolidityParser.parseFunc(code)
      func.isSuccess shouldBe true
      val function = func.get.value
      function.name shouldBe "burnFrom"
      function.modifiers shouldBe ModifierList(Set(Invocation("onlyBy"), Invocation("public1")), Some(Public), Some(View))
    }

    "parse expr" in {
      val expr = "((1+ 1*2)+(3*4*5))/3"
      val e    = SolidityParser.parseExpr(expr)
      e.isSuccess shouldBe true
      e.get.value shouldBe 21

      val exprV = "a + b"
      val value = SolidityParser.parseExpr(exprV, List("a" -> 1, "b" -> 2).toMap)
      value.isSuccess shouldBe true
      value.get.value shouldBe 3
    }

    "parse erc20" in {
      val parseResult  = SolidityParser.parseContract(code)
      val contract     = parseResult.get.value
      val erc20Methods = contract.toABI().methods.map(method => method.name -> method).toMap

      val name = erc20Methods("name")
        .decode(
          hex"0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000c494f54206f6e20436861696e0000000000000000000000000000000000000000")
      name.isRight shouldBe true
      name.right.get shouldBe List("IOT on Chain").asJson

      val symbol = erc20Methods("symbol")
        .decode(
          hex"0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000034954430000000000000000000000000000000000000000000000000000000000")
      symbol.isRight shouldBe true
      symbol.right.get shouldBe List("ITC").asJson

      val totalSupply =
        erc20Methods("totalSupply").decode(hex"0x00000000000000000000000000000000000000000052b7d2dcc80cd2e4000000")
      totalSupply.isRight shouldBe true
      totalSupply.right.get shouldBe List(BigInt(10).pow(26)).asJson

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
        .encode("[\"0x1234567890123456789012345678901234567890\", \"0x0987654321098765432109876543210987654321\", 233333]")
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

    "parse vaccine" in {
      val parseResult    = SolidityParser.parseContract(code2)
      val contract       = parseResult.get.value
      val vaccineMethods = contract.toABI().methods.map(method => method.name -> method).toMap

      val minter = vaccineMethods("minter")
        .decode(hex"0x0000000000000000000000001234567890123456789012345678901234567890")
      minter.isRight shouldBe true
      minter.right.get shouldBe List("0x1234567890123456789012345678901234567890").asJson

      val setValue = vaccineMethods("setValue").encode("[\"key1\", \"12345678901234567890123456789088\"]")
      setValue.isRight shouldBe true
      setValue.right.get shouldBe hex"0xec86cfad0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000046b6579310000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000203132333435363738393031323334353637383930313233343536373839303838"

      val getValueEncode = vaccineMethods("getValue").encode("[\"key1\"]")
      getValueEncode.isRight shouldBe true
      getValueEncode.right.get shouldBe hex"0x960384a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000046b65793100000000000000000000000000000000000000000000000000000000"

      val getValue = vaccineMethods("getValue").decode(
        hex"0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000c494f54206f6e20436861696e0000000000000000000000000000000000000000")
      getValue.isRight shouldBe true
      getValue.right.get shouldBe List("IOT on Chain").asJson

      val getValue2 = vaccineMethods("getValue").decode(
        hex"0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000676616c7565310000000000000000000000000000000000000000000000000000")
      getValue2.isRight shouldBe true
      getValue2.right.get shouldBe List("value1").asJson

      val batchSetValues = vaccineMethods("batchSetValues").encode("[[\"key2\", \"key3\"], [\"value2\", \"value3\"]]")
      batchSetValues.isRight shouldBe true
      batchSetValues.right.get shouldBe hex"0xd885851e0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000046b6579320000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000046b65793300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000676616c7565320000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000676616c7565330000000000000000000000000000000000000000000000000000"

      val batchGetValues = vaccineMethods("batchGetValues").encode("[[\"key1\", \"key2\", \"key3\"]]")
      batchGetValues.isRight shouldBe true
      batchGetValues.right.get shouldBe hex"0xbd7166a800000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000003000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000046b6579310000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000046b6579320000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000046b65793300000000000000000000000000000000000000000000000000000000"

      val batchGetValuesDecode = vaccineMethods("batchGetValues").decode(
        hex"0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000003000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000676616c7565310000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000676616c7565320000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000676616c7565330000000000000000000000000000000000000000000000000000")
      batchGetValuesDecode.isRight shouldBe true
      batchGetValuesDecode.right.get shouldBe List(List("value1", "value2", "value3")).asJson
    }

    "parse EOS contract" in {
      val parseResult = SolidityParser.parseSource(eosCode)

      parseResult.isSuccess shouldBe true

      val sources = parseResult.get.value
      val dsToken = sources.ABI.find(_.name == "DSToken")

      dsToken.nonEmpty shouldBe true
    }

    "parse code3" in {
      val parseResult = SolidityParser.parseSource(code3)

      parseResult.isSuccess shouldBe true
    }

    "parse error code" in {
      val parseResult = SolidityParser.parseSource("""pragma solidity 0.4.24;
          |
          |contract SimpleStorage {
          |    uint storedData;
          |
          |    function set(uint x) public {
          |        storedData = x;
          |    }
          |
          |    function get() public view returns (uint) {
          |        return storedData;
          |    }
        """.stripMargin)

      parseResult.isSuccess shouldBe false
    }

    "parse vaccine code" in {
      val parseResult = SolidityParser.parseSource("""pragma solidity >=0.4.0 <0.6.0;
          |pragma experimental ABIEncoderV2;
          |
          |contract Vaccine {
          |    address public minter;
          |
          |    mapping (string => string) private values;
          |
          |    function Vaccine() public {
          |        minter = msg.sender;
          |    }
          |
          |    function setValue(string memory key, string memory newValue) public {
          |        // require(msg.sender == minter,"没有权限");
          |        values[key] = newValue;
          |    }
          |
          |    function batchSetValues(string[] memory keys,string[] memory newValues) public {
          |        // require(msg.sender == minter,"没有权限");
          |        // require(keys.length == newValues.length,"参数不匹配");
          |        for (uint i = 0;i<keys.length;i++) {
          |            values[keys[i]] = newValues[i];
          |        }
          |    }
          |
          |    function getValue(string memory key) public view returns (string memory){
          |        // require(msg.sender == minter,"没有权限");
          |        return values[key];
          |    }
          |
          |    function batchGetValues(string[] memory keys) public view returns (string[] memory){
          |        // require(msg.sender == minter,"没有权限");
          |        string[] memory list = new string[](keys.length);
          |        for (uint i = 0;i<keys.length;i++) {
          |            list[i] = values[keys[i]];
          |        }
          |        return list;
          |    }
          |}
        """.stripMargin)

      parseResult.isSuccess shouldBe true
    }

    "parse itc mapping code" in {
      val parseResult = SolidityParser.parseSource("""pragma solidity >=0.4.0 <0.5.0;
          |
          |
          |/**
          | * @title SafeMath
          | * @dev Unsigned math operations with safety checks that revert on error.
          | * https://github.com/OpenZeppelin/openzeppelin-solidity/blob/master/contracts/math/SafeMath.sol
          | */
          |library SafeMath {
          |
          |    function div(uint256 a, uint256 b) internal pure returns (uint256) {
          |
          |        require(b > 0);
          |        uint256 c = a / b;
          |        return c;
          |    }
          |}
          |
          |/**
          | * @title Token
          | * @dev API interface for interacting with the ITC Token contract
          | */
          |interface Token {
          |
          |  function transfer(address _to, uint256 _value) external;
          |
          |  function balanceOf(address _owner) external returns (uint256 balance);
          |}
          |
          |
          |/**
          | * @title Ownable
          | * @dev The Ownable contract has an owner address, and provides basic authorization control
          | * https://github.com/OpenZeppelin/openzeppelin-solidity/blob/master/contracts/ownership/Ownable.sol
          | */
          |contract Ownable {
          |     address public _owner;
          |
          |    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);
          |
          |    constructor () internal {
          |        _owner = msg.sender;
          |        emit OwnershipTransferred(address(0), _owner);
          |    }
          |
          |    modifier onlyOwner() {
          |        require(isOwner());
          |        _;
          |    }
          |
          |    function isOwner() public view returns (bool) {
          |        return msg.sender == _owner;
          |    }
          |
          |    function transferOwnership(address newOwner) public onlyOwner {
          |        _transferOwnership(newOwner);
          |    }
          |
          |    function _transferOwnership(address newOwner) internal {
          |        require(newOwner != address(0));
          |        emit OwnershipTransferred(_owner, newOwner);
          |        _owner = newOwner;
          |    }
          |}
          |
          |contract ITCMapping is Ownable{
          |
          |    using SafeMath for uint256;
          |
          |    //ITC合约
          |    Token public token;
          |
          |    //ITC和ITG发放比例
          |    uint ratio = 1;
          |
          |    constructor(address tokenAddress) public{
          |
          |        token = Token(tokenAddress);
          |    }
          |
          |    /**
          |    * @dev 接收
          |    */
          |    function() payable external{
          |    }
          |
          |    /**
          |    * @dev 批量映射ITC和ITG
          |    */
          |    function batchTransfer(address[] memory addresses,uint256[] memory values) public payable onlyOwner{
          |
          |        require(addresses.length == values.length,'param error');
          |
          |        uint length = addresses.length;
          |        for (uint i=0 ; i< length ; i++){
          |
          |            uint256 itgBalance = SafeMath.div(values[i],ratio);
          |
          |            require(token.balanceOf(address(this))>values[i],'Insufficient ITC balance');
          |            require(address(this).balance>itgBalance,'Insufficient ITG balance');
          |
          |            //发放ITC
          |            token.transfer(addresses[i],values[i]);
          |
          |            //发放ITG
          |            addresses[i].transfer(itgBalance);
          |        }
          |    }
          |
          |    /**
          |    * @dev 发送剩余ITC至合约拥有人
          |    */
          |    function transferITCToOwner() public onlyOwner{
          |
          |        address contractAddress = address(this);
          |        uint256 balance = token.balanceOf(contractAddress);
          |        token.transfer(msg.sender,balance);
          |    }
          |
          |    /**
          |    * @dev 销毁合约
          |    */
          |    function destruct() payable public onlyOwner {
          |
          |        //销毁合约前，先确保合约地址的ITC已经转移完毕
          |        address contractAddress = address(this);
          |        require(token.balanceOf(contractAddress) == 0,'ITC balance is not empty, please transfer ITC first');
          |
          |        selfdestruct(msg.sender); // 销毁合约
          |    }
          |}""".stripMargin)

      parseResult.isSuccess shouldBe true
    }
  }
}
