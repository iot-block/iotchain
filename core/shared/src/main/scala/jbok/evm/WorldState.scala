package jbok.evm

import cats.Foldable
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.common._
import jbok.core.ledger.History
import jbok.core.models.{Account, Address, UInt256}
import jbok.core.store.namespaces
import jbok.crypto._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.persistent.{DBErr, StageKeyValueDB}
import scodec.bits._
import scodec.bits.ByteVector

final case class WorldState[F[_]](
    history: History[F],
    accountProxy: StageKeyValueDB[F, Address, Account],
    stateRootHash: ByteVector = MerklePatriciaTrie.emptyRootHash,
    touchedAccounts: Set[Address] = Set.empty[Address],
    contractStorages: Map[Address, Storage[F]] = Map.empty[Address, Storage[F]],
    contractCodes: Map[Address, ByteVector] = Map.empty[Address, ByteVector],
    accountStartNonce: UInt256 = UInt256.Zero,
    noEmptyAccounts: Boolean = true
)(implicit F: Sync[F]) {
  private[this] val log = jbok.common.log.getLogger("WorldState")

  // FIXME
  def getBlockHash(number: UInt256): F[Option[UInt256]] =
    Sync[F].pure(Some(UInt256(ByteVector(number.toString.getBytes).kec256)))

  def getAccountOpt(address: Address): OptionT[F, Account] =
    OptionT(accountProxy.getOpt(address))

  def putAccount(address: Address, account: Account): WorldState[F] =
    this.copy(accountProxy = accountProxy.put(address, account))

  def delAccount(address: Address): WorldState[F] =
    this.copy(accountProxy = accountProxy.del(address))

  def getEmptyAccount: Account = Account.empty(accountStartNonce)

  def touchAccounts(addresses: Address*): WorldState[F] =
    if (noEmptyAccounts) {
      this.copy(touchedAccounts = touchedAccounts ++ addresses.toSet)
    } else {
      this
    }

  def clearTouchedAccounts: WorldState[F] =
    this.copy(touchedAccounts = Set.empty)

  def getAccount(address: Address): F[Account] =
    getAccountOpt(address).getOrElseF(F.raiseError(DBErr.NotFound))

  def getStorage(address: Address): F[Storage[F]] =
    OptionT.fromOption[F](contractStorages.get(address)).getOrElseF(getOriginalStorage(address))

  def getOriginalStorage(address: Address): F[Storage[F]] =
    for {
      storageRoot <- getAccountOpt(address).map(_.storageRoot).value
      mpt         <- MerklePatriciaTrie[F](namespaces.Node, history.db, storageRoot)
      s = StageKeyValueDB[F, UInt256, UInt256](namespaces.empty, mpt)
    } yield Storage[F](s)

  def putStorage(address: Address, storage: Storage[F]): WorldState[F] =
    this.copy(contractStorages = contractStorages + (address -> storage))

  def getCode(address: Address): F[ByteVector] =
    OptionT.fromOption[F](contractCodes.get(address)).getOrElseF {
      val code = for {
        account <- getAccountOpt(address)
        code    <- OptionT(history.getCode(account.codeHash))
      } yield code

      code.getOrElse(ByteVector.empty)
    }

  def putCode(address: Address, code: ByteVector): WorldState[F] =
    this.copy(contractCodes = contractCodes + (address -> code))

  def combineTouchedAccounts(world: WorldState[F]): WorldState[F] =
    this.copy(touchedAccounts = world.touchedAccounts ++ touchedAccounts)

  def newEmptyAccount(address: Address): WorldState[F] =
    putAccount(address, getEmptyAccount)

  def accountExists(address: Address): F[Boolean] =
    getAccountOpt(address).isDefined

  def getBalance(address: Address): F[UInt256] =
    getAccountOpt(address).map(a => UInt256(a.balance)).getOrElse(UInt256.Zero)

  def transfer(from: Address, to: Address, value: UInt256): F[WorldState[F]] =
    Sync[F].ifM((from == to).pure[F] || isZeroValueTransferToNonExistentAccount(to, value))(
      ifTrue = touchAccounts(from).pure[F],
      ifFalse = guaranteedTransfer(from, to, value).map(_.touchAccounts(from, to))
    )

  def guaranteedTransfer(from: Address, to: Address, value: UInt256): F[WorldState[F]] =
    for {
      debited  <- getAccount(from).map(_.increaseBalance(-value))
      credited <- getAccountOpt(to).getOrElse(getEmptyAccount).map(_.increaseBalance(value))
    } yield putAccount(from, debited).putAccount(to, credited)

  /**
    * IF EIP-161 is in effect this sets new contract's account initial nonce to 1 over the default value
    * for the given network (usually zero)
    * Otherwise it's no-op
    */
  def initialiseAccount(newAddress: Address): F[WorldState[F]] =
    if (!noEmptyAccounts) {
      Sync[F].pure(this)
    } else {
      for {
        newAccount <- getAccountOpt(newAddress).getOrElse(getEmptyAccount)
        account = newAccount.copy(nonce = accountStartNonce + 1)
      } yield putAccount(newAddress, account)
    }

  /**
    * In case of transfer to self, during selfdestruction the ether is actually destroyed
    * see https://github.com/ethereum/wiki/wiki/Subtleties/d5d3583e1b0a53c7c49db2fa670fdd88aa7cabaf#other-operations
    * and https://github.com/ethereum/go-ethereum/blob/ff9a8682323648266d5c73f4f4bce545d91edccb/core/state/statedb.go#L322
    */
  def removeAllEther(address: Address): F[WorldState[F]] =
    for {
      debited <- getAccount(address)
    } yield putAccount(address, debited.copy(balance = 0)).touchAccounts(address)

  /** Creates a new address based on the address and nonce of the creator. YP equation 82 */
  def createContractAddress(creatorAddr: Address): F[Address] =
    for {
      creatorAccount <- getAccount(creatorAddr)
    } yield {
      val hash = (creatorAddr, creatorAccount.nonce - 1).asValidBytes.kec256
      Address.apply(hash)
    }

  def createContractAddressWithSalt(creatorAddr: Address, salt: ByteVector, initCode: ByteVector): F[Address] =
    F.pure(Address((hex"0xff" ++ creatorAddr.bytes ++ salt ++ initCode.kec256).kec256.drop(12)))

  /**
    * Increases the creator's nonce and creates a new address based on the address and the new nonce of the creator
    *
    * @param creatorAddr, the address of the creator of the new address
    * @return the new address and the state world after the creator's nonce was increased
    */
  def createAddressWithOpCode(creatorAddr: Address): F[(Address, WorldState[F])] =
    for {
      creatorAccount <- getAccount(creatorAddr)
      updatedWorld = putAccount(creatorAddr, creatorAccount.increaseNonce())
      createdAddress <- updatedWorld.createContractAddress(creatorAddr)
    } yield createdAddress -> updatedWorld

  def create2AddressWithOpCode(creatorAddr: Address,
                               salt: ByteVector,
                               initCode: ByteVector): F[(Address, WorldState[F])] =
    for {
      creatorAccount <- getAccount(creatorAddr)
      updateWorld = putAccount(creatorAddr, creatorAccount.increaseNonce())
      createdAddress <- updateWorld.createContractAddressWithSalt(creatorAddr, salt, initCode)
    } yield createdAddress -> updateWorld

  /**
    * Determines if account of provided address is dead.
    * According to EIP161: An account is considered dead when either it is non-existent or it is empty
    *
    * @param address, the address of the checked account
    * @return true if account is dead, false otherwise
    */
  def isAccountDead(address: Address): F[Boolean] =
    getAccountOpt(address).forall(_.isEmpty(accountStartNonce))

  def nonEmptyCodeOrNonce(address: Address): F[Boolean] =
    getAccountOpt(address).exists(_.nonEmptyCodeOrNonce(accountStartNonce))

  def isZeroValueTransferToNonExistentAccount(address: Address, value: UInt256): F[Boolean] =
    noEmptyAccounts.pure[F] && (value == UInt256(0)).pure[F] && !accountExists(address)

  def persisted: F[WorldState[F]] =
    for {
      world1 <- persistCode(this)
      world2 <- persistStorage(world1)
      world3 <- persistAccount(world2)
    } yield world3

  private def persistCode(world: WorldState[F]): F[WorldState[F]] =
    Foldable[List].foldLeftM(world.contractCodes.toList, world) {
      case (updatedWorldState, (address, code)) =>
        for {
          account <- updatedWorldState.getAccount(address)
          codeHash = code.kec256
          _ <- updatedWorldState.history.putCode(codeHash, code)
        } yield {
          updatedWorldState.copy(
            accountProxy = updatedWorldState.accountProxy + (address -> account.copy(codeHash = codeHash)),
            contractCodes = Map.empty
          )
        }
    }

  private def persistStorage(world: WorldState[F]): F[WorldState[F]] =
    Foldable[List].foldLeftM(world.contractStorages.toList, world) {
      case (updatedWorldState, (address, storageTrie)) =>
        for {
          persistedStorage   <- storageTrie.commit
          newStorageRootHash <- persistedStorage.db.inner.asInstanceOf[MerklePatriciaTrie[F]].getRootHash
          account            <- updatedWorldState.getAccount(address)
        } yield
          updatedWorldState.copy(
            contractStorages = updatedWorldState.contractStorages + (address -> persistedStorage),
            accountProxy = updatedWorldState.accountProxy + (address         -> account.copy(storageRoot = newStorageRootHash))
          )
    }

  private def persistAccount(world: WorldState[F]): F[WorldState[F]] =
    for {
      committed        <- world.accountProxy.commit
      newStateRootHash <- committed.inner.asInstanceOf[MerklePatriciaTrie[F]].getRootHash
    } yield world.copy(accountProxy = committed, stateRootHash = newStateRootHash)
}
