package jbok.evm

import cats.Foldable
import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.common._
import jbok.core.models.{Account, Address, UInt256}
import jbok.core.store.EvmCodeStore
import jbok.crypto._
import jbok.crypto.authds.mpt.{MPTrie, MPTrieStore}
import jbok.persistent.{KeyValueDB, SnapshotKeyValueStore}
import scodec.bits.ByteVector
import shapeless._
import jbok.codec.rlp.codecs._

case class WorldStateProxy[F[_]: Sync](
    db: KeyValueDB[F],
    accountProxy: SnapshotKeyValueStore[F, Address, Account],
    touchedAccounts: Set[Address],
    accountCodes: Map[Address, ByteVector],
    contractStorages: Map[Address, Storage[F]],
    evmCodeStorage: EvmCodeStore[F],
    stateRootHash: ByteVector,
    accountStartNonce: UInt256,
    noEmptyAccounts: Boolean,
    getBlockHash: UInt256 => F[Option[UInt256]]
) {
  def getAccountOpt(address: Address): OptionT[F, Account] =
    OptionT(accountProxy.getOpt(address))

  def saveAccount(address: Address, account: Account): WorldStateProxy[F] =
    this.copy(accountProxy = accountProxy.put(address, account))

  def deleteAccount(address: Address): WorldStateProxy[F] =
    this.copy(accountProxy = accountProxy.del(address))

  def getEmptyAccount: Account = Account.empty(accountStartNonce)

  def touchAccounts(addresses: Address*): WorldStateProxy[F] =
    if (noEmptyAccounts) {
      this.copy(touchedAccounts = touchedAccounts ++ addresses.toSet)
    } else {
      this
    }

  def clearTouchedAccounts: WorldStateProxy[F] =
    this.copy(touchedAccounts = Set.empty)

  def getAccount(address: Address): F[Account] =
    getAccountOpt(address).value.map(_.get)

  def getCode(address: Address): F[ByteVector] =
    OptionT.fromOption[F](accountCodes.get(address)).getOrElseF {
      val code = for {
        account <- getAccountOpt(address)
        code <- OptionT(evmCodeStorage.getOpt(account.codeHash))
      } yield code

      code.getOrElse(ByteVector.empty)
    }

  def saveCode(address: Address, code: ByteVector): WorldStateProxy[F] =
    this.copy(accountCodes = accountCodes + (address -> code))

  def getStorage(address: Address): F[Storage[F]] =
    OptionT.fromOption[F](contractStorages.get(address)).getOrElseF {
      for {
        storageRoot <- getAccountOpt(address).map(_.storageRoot).getOrElse(Account.EmptyStorageRootHash)
        mpt <- MPTrieStore[F, UInt256, UInt256](db)
        s = SnapshotKeyValueStore(mpt)
      } yield Storage[F](s)
    }

  def saveStorage(address: Address, storage: Storage[F]): WorldStateProxy[F] =
    this.copy(contractStorages = contractStorages + (address -> storage))

  def combineTouchedAccounts(world: WorldStateProxy[F]): WorldStateProxy[F] =
    this.copy(touchedAccounts = world.touchedAccounts ++ touchedAccounts)

  def newEmptyAccount(address: Address): WorldStateProxy[F] =
    saveAccount(address, getEmptyAccount)

  def accountExists(address: Address): F[Boolean] =
    getAccountOpt(address).isDefined

  def getBalance(address: Address): F[UInt256] =
    getAccountOpt(address).map(a => UInt256(a.balance)).getOrElse(UInt256.Zero)

  def transfer(from: Address, to: Address, value: UInt256): F[WorldStateProxy[F]] =
    Sync[F].ifM((from == to).pure[F] || isZeroValueTransferToNonExistentAccount(to, value))(
      ifTrue = touchAccounts(from).pure[F],
      ifFalse = guaranteedTransfer(from, to, value).map(_.touchAccounts(from, to))
    )

  def guaranteedTransfer(from: Address, to: Address, value: UInt256): F[WorldStateProxy[F]] =
    for {
      debited <- getAccount(from).map(_.increaseBalance(-value))
      credited <- getAccountOpt(to).getOrElse(getEmptyAccount).map(_.increaseBalance(value))
    } yield saveAccount(from, debited).saveAccount(to, credited)

  /**
    * IF EIP-161 is in effect this sets new contract's account initial nonce to 1 over the default value
    * for the given network (usually zero)
    * Otherwise it's no-op
    */
  def initialiseAccount(newAddress: Address): F[WorldStateProxy[F]] =
    if (!noEmptyAccounts) {
      Sync[F].pure(this)
    } else {
      for {
        newAccount <- getAccountOpt(newAddress).getOrElse(getEmptyAccount)
        account = newAccount.copy(nonce = accountStartNonce + 1)
      } yield saveAccount(newAddress, account)
    }

  /**
    * In case of transfer to self, during selfdestruction the ether is actually destroyed
    * see https://github.com/ethereum/wiki/wiki/Subtleties/d5d3583e1b0a53c7c49db2fa670fdd88aa7cabaf#other-operations
    * and https://github.com/ethereum/go-ethereum/blob/ff9a8682323648266d5c73f4f4bce545d91edccb/core/state/statedb.go#L322
    */
  def removeAllEther(address: Address): F[WorldStateProxy[F]] =
    for {
      debited <- getAccount(address)
    } yield saveAccount(address, debited.copy(balance = 0)).touchAccounts(address)

  /**
    * Creates a new address based on the address and nonce of the creator. YP equation 82
    *
    * @param creatorAddr, the address of the creator of the new address
    * @return the new address
    */
  def createAddress(creatorAddr: Address): F[Address] =
    for {
      creatorAccount <- getAccount(creatorAddr)
    } yield {
      val hash = RlpCodec.encode(creatorAddr :: (creatorAccount.nonce - 1) :: HNil).require.bytes.kec256
      Address.apply(hash)
    }

  /**
    * Increases the creator's nonce and creates a new address based on the address and the new nonce of the creator
    *
    * @param creatorAddr, the address of the creator of the new address
    * @return the new address and the state world after the creator's nonce was increased
    */
  def createAddressWithOpCode(creatorAddr: Address): F[(Address, WorldStateProxy[F])] =
    for {
      creatorAccount <- getAccount(creatorAddr)
      updatedWorld = saveAccount(creatorAddr, creatorAccount.increaseNonce())
      createdAddress <- updatedWorld.createAddress(creatorAddr)
    } yield createdAddress -> updatedWorld

  /**
    * Determines if account of provided address is dead.
    * According to EIP161: An account is considered dead when either it is non-existent or it is empty
    *
    * @param address, the address of the checked account
    * @return true if account is dead, false otherwise
    */
  def isAccountDead(address: Address): F[Boolean] =
    getAccountOpt(address).forall(_.isEmpty(accountStartNonce))

  def nonEmptyCodeOrNonceAccount(address: Address): F[Boolean] =
    getAccountOpt(address).exists(_.nonEmptyCodeOrNonce(accountStartNonce))

  def isZeroValueTransferToNonExistentAccount(address: Address, value: UInt256): F[Boolean] =
    noEmptyAccounts.pure[F] && (value == UInt256(0)).pure[F] && !accountExists(address)
}

object WorldStateProxy {
  def inMemory[F[_]: Sync](db: KeyValueDB[F], noEmptyAccounts: Boolean = false): F[WorldStateProxy[F]] =
    for {
      mpt <- MPTrieStore[F, Address, Account](db)
      accountProxy = SnapshotKeyValueStore(mpt)
      evmCodeStorage = new EvmCodeStore[F](db)
    } yield
      WorldStateProxy[F](
        db,
        accountProxy,
        Set.empty,
        Map.empty,
        Map.empty,
        evmCodeStorage,
        MPTrie.emptyRootHash,
        UInt256.Zero,
        noEmptyAccounts,
        (_: UInt256) => Sync[F].pure(None)
      )

  def persist[F[_]: Sync](world: WorldStateProxy[F]): F[WorldStateProxy[F]] =
    Kleisli(persistCode[F] _).andThen(persistContractStorage[F] _).andThen(persistAccountsStateTrie[F] _).run(world)

  def persistCode[F[_]: Sync](world: WorldStateProxy[F]): F[WorldStateProxy[F]] =
    Foldable[List].foldLeftM(world.accountCodes.toList, world) {
      case (updatedWorldState, (address, code)) =>
        for {
          account <- updatedWorldState.getAccount(address)
          codeHash = code.kec256
          _ <- updatedWorldState.evmCodeStorage.put(codeHash, code)
        } yield {
          updatedWorldState.copy(
            accountProxy = updatedWorldState.accountProxy + (address -> account.copy(codeHash = codeHash)),
            accountCodes = Map.empty
          )
        }
    }

  def persistContractStorage[F[_]: Sync](world: WorldStateProxy[F]): F[WorldStateProxy[F]] =
    Foldable[List].foldLeftM(world.contractStorages.toList, world) {
      case (updatedWorldState, (address, storageTrie)) =>
        for {
          persistedStorage <- storageTrie.commit
          newStorageRootHash <- persistedStorage.db.inner.asInstanceOf[MPTrieStore[F, UInt256, UInt256]].getRootHash
          account <- updatedWorldState.getAccount(address)
        } yield
          updatedWorldState.copy(
            contractStorages = updatedWorldState.contractStorages + (address -> persistedStorage),
            accountProxy = updatedWorldState.accountProxy + (address -> account.copy(storageRoot = newStorageRootHash))
          )
    }

  def persistAccountsStateTrie[F[_]: Sync](world: WorldStateProxy[F]): F[WorldStateProxy[F]] =
    for {
      p <- world.accountProxy.commit()
    } yield world.copy(accountProxy = p)
}
