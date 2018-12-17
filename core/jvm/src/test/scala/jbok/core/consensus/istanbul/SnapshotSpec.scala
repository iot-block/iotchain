//package jbok.core.consensus.istanbul
//
//import cats.effect.IO
//import jbok.JbokSpec
//import jbok.codec.rlp.RlpCodec
//import jbok.core.config.{GenesisConfig, reference}
//import jbok.core.models.{Address, BlockHeader}
//import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
//import jbok.persistent.KeyValueDB
//import scodec.bits.ByteVector
//import jbok.codec.rlp.implicits._
//import cats.implicits._
//import cats.effect.implicits._
//import jbok.core.ledger.History
//
//import scala.collection.mutable
//
//case class TestVote(
//    signer: String,
//    voted: String = "",
//    auth: Boolean = false
//)
//
//case class Test(signers: List[String], votes: List[TestVote], results: List[String], epoch: BigInt = 30000)
//
//trait SnapshotFixture {
//  private def fillExtraData(signers: List[Address]): ByteVector = {
//    val extra = IstanbulExtra(signers, ByteVector.empty, List.empty)
//    ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra).require.bytes
//  }
//
//  def mkHistory(signers: List[Address]) = {
//    val extra   = fillExtraData(signers)
//    val config  = reference.genesis.copy(extraData = extra)
//    implicit val chainId = config.chainId
//    val db      = KeyValueDB.inmem[IO].unsafeRunSync()
//    val history = History[IO](db).unsafeRunSync()
//    history.initGenesis(config).unsafeRunSync()
//    history
//  }
//
//  val accounts: mutable.Map[String, KeyPair] = mutable.Map.empty
//  def address(account: String): Address = {
//    if (!accounts.contains(account)) {
//      accounts += (account -> Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
//    }
//    Address(accounts(account))
//  }
//
//  def sign(header: BlockHeader, signer: String)(implicit chainId: BigInt): BlockHeader = {
//    if (!accounts.contains(signer)) {
//      accounts += (signer -> Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
//    }
//    val sig       = Signature[ECDSA].sign(Istanbul.sigHash(header).toArray, accounts(signer),chainId).unsafeRunSync()
//    val signed    = header.copy(extraData = header.extraData.dropRight(65) ++ ByteVector(sig.bytes))
//    val recovered = Istanbul.ecrecover(signed)
//    require(recovered == Address(accounts(signer)), s"recovered: ${recovered}, signer: ${accounts(signer)}")
//    signed
//  }
//}
//
//class SnapshotSpec extends JbokSpec {
//
//  def check(test: Test) = new SnapshotFixture {
//    val config  = IstanbulConfig()
//    val signers = test.signers.map(signer => address(signer))
//    val history = mkHistory(signers) // genesis signers
//
//    // Assemble a chain of headers from the cast votes
//    val headers: List[BlockHeader] = test.votes.zipWithIndex.map {
//      case (v, i) =>
//        val number   = BigInt(i) + 1
//        val time     = i * config.period.toSeconds
//        val coinbase = address(v.voted)
//        val extra    = ByteVector.fill(Istanbul.extraVanity)(0)
//        val header = BlockHeader.empty
//          .copy(
//            number = number,
//            unixTimestamp = time,
//            beneficiary = coinbase.bytes,
//            extraData = extra,
//            nonce = if (v.auth) Istanbul.nonceAuthVote else Istanbul.nonceDropVote
//          )
//        sign(header, v.signer) // signer vote to authorize/deauthorize the beneficiary
//    }
//
//  }
//}
