package jbok.core.keystore

import java.nio.file.Paths
import java.security.SecureRandom
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

import better.files._
import cats.effect.{Resource, Sync}
import cats.implicits._
import io.circe.parser._
import io.circe.syntax._
import jbok.common.log.Logger
import jbok.common.{FileUtil, Terminal}
import jbok.core.config.KeyStoreConfig
import jbok.core.keystore.KeyStoreError.KeyNotFound
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits.ByteVector

import scala.util.Try

final class KeyStorePlatform[F[_]](config: KeyStoreConfig)(implicit F: Sync[F]) extends KeyStore[F] {
  private[this] val log = Logger[F]

  private val keyStoreDir = Paths.get(config.dir)

  private val secureRandom = new SecureRandom()

  private val keyLength = 32

  override def newAccount(passphrase: String): F[Address] =
    for {
      keyPair <- Signature[ECDSA].generateKeyPair[F]()
      encKey  <- EncryptedKey(keyPair.secret, passphrase, secureRandom)
      _       <- save(encKey)
    } yield encKey.address

  override def readPassphrase(prompt: String): F[String] =
    for {
      _          <- Terminal.putStrLn[F](prompt)
      passphrase <- Terminal.readPassword[F]("Passphrase:")
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
      wallet <- key.decrypt(passphrase) match {
        case Left(_)       => F.raiseError(KeyStoreError.DecryptionFailed)
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

  private def save(encryptedKey: EncryptedKey): F[Unit] = {
    val json = encryptedKey.asJson.spaces2
    val name = fileName(encryptedKey)
    val path = keyStoreDir.resolve(name)

    for {
      alreadyInKeyStore <- containsAccount(encryptedKey)
      _ <- if (alreadyInKeyStore) {
        F.raiseError(KeyStoreError.KeyAlreadyExist)
      } else {
        log.info(s"saving key into ${path.toAbsolutePath}") >>
          FileUtil[F].dump(json, path).attempt.flatMap {
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

  private def fileName(encryptedKey: EncryptedKey): String =
    s"${encryptedKey.address.bytes.toHex}.json"

  private def listFiles(): F[List[File]] =
    FileUtil[F].open(keyStoreDir, asDirectory = true).flatMap { file =>
      if (!file.exists || !file.isDirectory) {
        F.pure(Nil)
      } else {
        F.delay(file.list.toList)
      }
    }

  private def containsAccount(encKey: EncryptedKey): F[Boolean] =
    load(encKey.address).attemptT.isRight

  private def findKeyFile(address: Address): F[File] =
    for {
      files <- listFiles()
      matching <- files.find(_.name.startsWith(address.bytes.toHex)) match {
        case Some(file) => F.pure(file)
        case None       => F.raiseError(KeyNotFound)
      }
    } yield matching
}

object KeyStorePlatform {
  def withInitKey[F[_]](config: KeyStoreConfig)(implicit F: Sync[F]): F[KeyStorePlatform[F]] = {
    val keyStorePlatform = new KeyStorePlatform(config)
    val initKeyFile      = Try(Paths.get(config.initkey)).toOption

    initKeyFile.fold(F.delay(keyStorePlatform)) { keyFile =>
      for {
        json <- FileUtil[F].read(keyFile)
        key <- decode[EncryptedKey](json) match {
          case Left(_)  => F.raiseError(KeyStoreError.InvalidKeyFormat)
          case Right(k) => F.pure(k)
        }
        name = keyStorePlatform.fileName(key)
        path = keyStorePlatform.keyStoreDir.resolve(name)
        _ <- FileUtil[F].dump(json, path).attempt.flatMap {
          case Left(e)  => F.raiseError[Unit](KeyStoreError.IOError(e.toString))
          case Right(_) => F.unit
        }
      } yield keyStorePlatform
    }
  }

  def temporary[F[_]](implicit F: Sync[F]): Resource[F, KeyStore[F]] =
    FileUtil[F].temporaryDir().map { file =>
      new KeyStorePlatform[F](KeyStoreConfig("", file.pathAsString))
    }
}
