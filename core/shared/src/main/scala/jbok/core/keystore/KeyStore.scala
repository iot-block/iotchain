package jbok.core.keystore

import java.security.SecureRandom
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

import better.files._
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import jbok.core.keystore.KeyStoreError.KeyNotFound
import jbok.core.models.Address
import jbok.crypto.signature.{KeyPair, SecP256k1}
import scodec.bits.ByteVector
import jbok.codec.json._

sealed trait KeyStoreError
object KeyStoreError {
  def keyNotFound: KeyStoreError = KeyNotFound
  def decryptionFailed: KeyStoreError = DecryptionFailed
  def invalidKeyFormat: KeyStoreError = InvalidKeyFormat
  def ioError(e: Throwable): KeyStoreError = IOError(e.toString)
  def ioError(e: String): KeyStoreError = IOError(e)
  def duplicateKeySaved: KeyStoreError = DuplicateKeySaved

  case object KeyNotFound extends KeyStoreError
  case object DecryptionFailed extends KeyStoreError
  case object InvalidKeyFormat extends KeyStoreError
  case class IOError(msg: String) extends KeyStoreError
  case object DuplicateKeySaved extends KeyStoreError
}

trait KeyStore[F[_]] {
  def newAccount(passphrase: String): F[Either[KeyStoreError, Address]]

  def importPrivateKey(key: ByteVector, passphrase: String): F[Either[KeyStoreError, Address]]

  def listAccounts: F[Either[KeyStoreError, List[Address]]]

  def unlockAccount(address: Address, passphrase: String): F[Either[KeyStoreError, Wallet]]

  def deleteWallet(address: Address): F[Either[KeyStoreError, Boolean]]

  def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Either[KeyStoreError, Boolean]]

  def clear: F[Either[KeyStoreError, Boolean]]
}

class KeyStoreImpl[F[_]](keyStoreDir: File, secureRandom: SecureRandom)(implicit F: Sync[F]) extends KeyStore[F] {
  private[this] val log = org.log4s.getLogger

  private val keyLength = 32

  override def newAccount(passphrase: String): F[Either[KeyStoreError, Address]] = {
    val p = for {
      keyPair <- EitherT.right[KeyStoreError](SecP256k1.generateKeyPair[F])
      encKey = EncryptedKey(keyPair.secret, passphrase, secureRandom)
      _ <- save(encKey)
    } yield encKey.address
    p.value
  }

  override def importPrivateKey(key: ByteVector, passphrase: String): F[Either[KeyStoreError, Address]] = {
    if (key.length != keyLength) {
      log.warn(s"import key failed, incorrect key length ${key.length}")
      F.pure(Left(KeyStoreError.invalidKeyFormat))
    } else {
      val encKey = EncryptedKey(KeyPair.Secret(key), passphrase, secureRandom)
      save(encKey).map(_ => encKey.address).value
    }
  }

  override def listAccounts: F[Either[KeyStoreError, List[Address]]] = {
    val p = for {
      files <- listFiles()
      loaded <- files.sortBy(_.name).traverse(load)
    } yield loaded.map(_.address)
    p.value
  }

  override def unlockAccount(address: Address, passphrase: String): F[Either[KeyStoreError, Wallet]] = {
    val p = for {
      key <- load(address)
      wallet <- EitherT.fromEither[F](
        key
          .decrypt(passphrase)
          .leftMap {_ =>
            log.error(s"unlock account decryption failed")
            KeyStoreError.decryptionFailed
          }
          .map { secret =>
            log.info(s"unlocked wallet at ${address}")
            Wallet(address, secret)
          }
      )
    } yield wallet
    p.value
  }

  override def deleteWallet(address: Address): F[Either[KeyStoreError, Boolean]] = {
    val p = for {
      file <- findKeyFile(address)
      deleted <- deleteFile(file)
    } yield deleted
    p.value
  }

  override def changePassphrase(address: Address,
                                oldPassphrase: String,
                                newPassphrase: String): F[Either[KeyStoreError, Boolean]] = {
    val p = for {
      oldKey <- load(address)
      prvKey <- EitherT.fromEither[F](oldKey.decrypt(oldPassphrase).left.map(_ => KeyStoreError.decryptionFailed))
      keyFile <- findKeyFile(address)
      newEncKey = EncryptedKey(prvKey, newPassphrase, secureRandom)
      _ <- overwrite(keyFile, newEncKey)
    } yield true
    p.value
  }

  override def clear: F[Either[KeyStoreError, Boolean]] = {
    log.info(s"delete keyStoreDir ${keyStoreDir.pathAsString}")
    deleteFile(keyStoreDir).value
  }

  private[jbok] def save(encryptedKey: EncryptedKey): EitherT[F, KeyStoreError, Unit] = {
    val json = encryptedKey.asJson.spaces2
    val name = fileName(encryptedKey)
    val file = keyStoreDir / name

    for {
      alreadyInKeyStore <- containsAccount(encryptedKey)
      _ <- EitherT[F, KeyStoreError, Unit](if (alreadyInKeyStore) {
        F.pure(Left(KeyStoreError.duplicateKeySaved))
      } else {
        log.info(s"saving key into ${file.pathAsString}")
        F.delay(file.writeText(json)).attempt.map(_.leftMap(KeyStoreError.ioError).map(_ => ()))
      })
    } yield ()
  }

  private[jbok] def deleteFile(file: File): EitherT[F, KeyStoreError, Boolean] =
    EitherT(F.delay(file.delete()).attempt.map(_.leftMap(KeyStoreError.ioError))).map(_ => true)

  private[jbok] def overwrite(file: File, encKey: EncryptedKey): EitherT[F, KeyStoreError, Unit] = {
    val json = encKey.asJson.spaces2
    EitherT[F, KeyStoreError, Unit](
      F.delay(file.writeText(json)).attempt.map(_.leftMap(KeyStoreError.ioError).map(_ => ()))
    )
  }

  private[jbok] def load(address: Address): EitherT[F, KeyStoreError, EncryptedKey] =
    for {
      filename <- findKeyFile(address)
      key <- load(filename)
    } yield key

  private[jbok] def load(file: File): EitherT[F, KeyStoreError, EncryptedKey] =
    for {
      json <- EitherT[F, KeyStoreError, String](
        F.delay(file.lines.mkString("\n")).attempt.map(_.leftMap(KeyStoreError.ioError))
      )
      key <- EitherT[F, KeyStoreError, EncryptedKey](
        F.pure(
          decode[EncryptedKey](json)
            .leftMap(_ => KeyStoreError.invalidKeyFormat)
            .filterOrElse(k => file.name.endsWith(k.address.bytes.toHex), KeyStoreError.invalidKeyFormat)
        )
      )
    } yield key

  private[jbok] def fileName(encryptedKey: EncryptedKey): String = {
    val dateStr = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME).replace(':', '-')
    val addrStr = encryptedKey.address.bytes.toHex
    s"UTC--$dateStr--$addrStr"
  }

  private[jbok] def listFiles(): EitherT[F, KeyStoreError, List[File]] =
    EitherT(F.delay {
      if (!keyStoreDir.exists || !keyStoreDir.isDirectory) {
        Left(KeyStoreError.ioError(s"could not read ${keyStoreDir}"))
      } else {
        Right(keyStoreDir.list.toList)
      }
    })

  private[jbok] def containsAccount(encKey: EncryptedKey): EitherT[F, KeyStoreError, Boolean] =
    EitherT(load(encKey.address).value.map {
      case Right(_)          => Right(true)
      case Left(KeyNotFound) => Right(false)
      case Left(err)         => Left(err)
    })

  private[jbok] def findKeyFile(address: Address): EitherT[F, KeyStoreError, File] =
    for {
      files <- listFiles()
      matching <- EitherT[F, KeyStoreError, File](
        F.delay(
          files
            .find(_.name.endsWith(address.bytes.toHex))
            .map(Right(_))
            .getOrElse(Left(KeyNotFound))))
    } yield matching
}

object KeyStore {
  def apply[F[_]: Sync](keyStoreDir: String, secureRandom: SecureRandom): F[KeyStore[F]] = {
    val dir = File(keyStoreDir)
    for {
      _ <- if (!dir.isDirectory) {
        Sync[F].delay(dir.createIfNotExists(asDirectory = true, createParents = true)).attempt
      } else {
        Sync[F].unit
      }
    } yield {
      require(dir.isDirectory, s"could not create keystore directory ($dir)")
      new KeyStoreImpl[F](dir, secureRandom)
    }
  }
}
