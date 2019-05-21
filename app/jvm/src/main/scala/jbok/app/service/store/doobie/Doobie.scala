package jbok.app.service.store.doobie

import cats.effect.{Async, ContextShift, Resource}
import doobie._
import doobie.h2.H2Transactor
import doobie.hikari.HikariTransactor
import doobie.implicits._
import jbok.core.config.DatabaseConfig
import jbok.core.models.Address
import scodec.bits.ByteVector

object Doobie {
  // meta mappings
  implicit val metaByteVector: Meta[ByteVector] = Meta.StringMeta.imap[ByteVector](ByteVector.fromValidHex(_))(_.toHex)

  implicit val metaBigInt: Meta[BigInt] = Meta.StringMeta.imap[BigInt](BigInt.apply)(_.toString())

  implicit val metaAddress: Meta[Address] = Meta.StringMeta.imap[Address](Address.fromHex)(_.toString)

  final case class Test(name: String, age: Int)

  def desc(fields: String*): Fragment =
    fr"order by ${fields.mkString(",")} desc"

  def limit(number: Int, size: Int): Fragment =
    fr"limit ${size} offset ${(number - 1) * size}"

  def xa[F[_]](dbUrl: String, driver: String)(implicit F: Async[F], cs: ContextShift[F]): Resource[F, Transactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[F](32) // our connect EC
      te <- ExecutionContexts.cachedThreadPool[F]    // our transaction EC
      xa <- HikariTransactor.newHikariTransactor[F](
        driver,
        dbUrl,
        "", // username
        "", // password
        ce, // await connection here
        te // execute JDBC operations here
      )
    } yield xa

  def h2[F[_]](dbUrl: String)(implicit F: Async[F], cs: ContextShift[F]): Resource[F, Transactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[F](32) // our connect EC
      te <- ExecutionContexts.cachedThreadPool[F]    // our transaction EC
      xa <- H2Transactor.newH2Transactor[F](
        "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", // connect URL
        "sa", // username
        "", // password
        ce, // await connection here
        te // execute JDBC operations here
      )
    } yield xa

  def fromConfig[F[_]](db: DatabaseConfig)(implicit F: Async[F], cs: ContextShift[F]): Resource[F, Transactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[F](32) // our connect EC
      te <- ExecutionContexts.cachedThreadPool[F]    // our transaction EC
      xa <- H2Transactor.newH2Transactor[F](
        db.url,
        db.user,
        db.password,
        ce, // await connection here
        te // execute JDBC operations here
      )
    } yield xa
}
