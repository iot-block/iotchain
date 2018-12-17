package jbok.core.keystore

import java.security.SecureRandom
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

import better.files._
import cats.effect.Async
import cats.implicits._
import io.circe.parser._
import io.circe.syntax._
import jbok.core.keystore.KeyStoreError.KeyNotFound
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

class KeyStorePlatform[F[_]](keyStoreDir: File, secureRandom: SecureRandom)(implicit F: Async[F]) extends KeyStore[F] {
  private[this] val log = jbok.common.log.getLogger("KeyStore")

  private val keyLength = 32

  override def newAccount(passphrase: String): F[Address] =
    for {
      keyPair <- Signature[ECDSA].generateKeyPair[F]()
      encKey  <- EncryptedKey(keyPair.secret, passphrase, secureRandom)
      _       <- save(encKey)
    } yield encKey.address

  override def readPassphrase(prompt: String): F[String] =
    for {
      _          <- F.delay(println(prompt))
      passphrase <- F.delay((new scala.tools.jline_embedded.console.ConsoleReader).readLine(Character.valueOf(0)))
    } yield passphrase

  override def importPrivateKey(key: ByteVector, passphrase: String): F[Address] =
    if (key.length != keyLength) {
      log.warn(s"import key failed, incorrect key length ${key.length}")
      F.raiseError(KeyStoreError.InvalidKeyFormat)
    } else {
      EncryptedKey[F](KeyPair.Secret(key), passphrase, secureRandom).flatMap(encKey => save(encKey).as(encKey.address))
    }

  override def listAccounts: F[List[Address]] =
    for {
      files  <- listFiles()
      loaded <- files.sortBy(_.name).traverse(load)
    } yield loaded.map(_.address)

  override def unlockAccount(address: Address, passphrase: String): F[Wallet] =
    for {
      key <- load(address)
      wallet <- key
        .decrypt(passphrase) match {
        case Left(e)       => F.raiseError(KeyStoreError.DecryptionFailed)
        case Right(secret) => Wallet.fromSecret[F](secret)
      }
    } yield wallet

  override def deleteAccount(address: Address): F[Boolean] =
    for {
      file    <- findKeyFile(address)
      deleted <- deleteFile(file)
    } yield deleted

  override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Boolean] =
    for {
      oldKey <- load(address)
      prvKey <- oldKey.decrypt(oldPassphrase) match {
        case Left(_)  => F.raiseError(KeyStoreError.DecryptionFailed)
        case Right(s) => F.pure(s)
      }
      keyFile   <- findKeyFile(address)
      newEncKey <- EncryptedKey(prvKey, newPassphrase, secureRandom)
      _         <- overwrite(keyFile, newEncKey)
    } yield true

  override def isEmpty: F[Boolean] =
    listAccounts.map(_.isEmpty)

  private def save(encryptedKey: EncryptedKey): F[Unit] = {
    val json = encryptedKey.asJson.spaces2
    val name = fileName(encryptedKey)
    val file = keyStoreDir / name

    for {
      alreadyInKeyStore <- containsAccount(encryptedKey)
      _ <- if (alreadyInKeyStore) {
        F.raiseError(KeyStoreError.KeyAlreadyExist)
      } else {
        log.info(s"saving key into ${file.pathAsString}")
        F.delay(file.createIfNotExists(createParents = true).writeText(json)).attempt.flatMap {
          case Left(e)  => F.raiseError[Unit](KeyStoreError.IOError(e.toString))
          case Right(_) => F.unit
        }
      }
    } yield ()
  }

  private def deleteFile(file: File): F[Boolean] =
    F.delay(file.delete()).attemptT.isRight

  private def overwrite(file: File, encKey: EncryptedKey): F[Unit] = {
    val json = encKey.asJson.spaces2
    F.delay(file.createIfNotExists(createParents = true).writeText(json)).attempt.flatMap {
      case Left(e)  => F.raiseError(KeyStoreError.IOError(e.toString))
      case Right(_) => F.unit
    }
  }

  private def load(address: Address): F[EncryptedKey] =
    for {
      filename <- findKeyFile(address)
      key      <- load(filename)
    } yield key

  private def load(file: File): F[EncryptedKey] =
    for {
      json <- F.delay(file.lines.mkString("\n")).attempt.flatMap {
        case Left(e)  => F.raiseError[String](KeyStoreError.IOError(e.toString))
        case Right(s) => F.pure(s)
      }
      key <- decode[EncryptedKey](json) match {
        case Left(_)  => F.raiseError(KeyStoreError.InvalidKeyFormat)
        case Right(k) => F.pure(k)
      }
    } yield key

  private def fileName(encryptedKey: EncryptedKey): String = {
    val dateStr = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME).replace(':', '-')
    val addrStr = encryptedKey.address.bytes.toHex
    s"UTC--$dateStr--$addrStr"
  }

  private def listFiles(): F[List[File]] =
    if (!keyStoreDir.exists || !keyStoreDir.isDirectory) {
      F.pure(Nil)
    } else {
      F.pure(keyStoreDir.list.toList)
    }

  private def containsAccount(encKey: EncryptedKey): F[Boolean] =
    load(encKey.address).attemptT.isRight

  private def findKeyFile(address: Address): F[File] =
    for {
      files <- listFiles()
      matching <- files.find(_.name.endsWith(address.bytes.toHex)) match {
        case Some(file) => F.pure(file)
        case None       => F.raiseError(KeyNotFound)
      }
    } yield matching
}

object KeyStorePlatform {
  def apply[F[_]](keyStoreDir: String)(implicit F: Async[F]): F[KeyStorePlatform[F]] =
    for {
      secureRandom <- F.delay(new SecureRandom())
      dir          <- F.delay(File(keyStoreDir))
    } yield new KeyStorePlatform[F](dir, secureRandom)
}
