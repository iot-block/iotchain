//package jbok.crypto.signature
//
//import cats.effect.IO
//import jbok.JbokAsyncSpec
//import jbok.common.testkit.ByteGen
//import scodec.bits.ByteVector
//
//class JsSignatureSpec extends JbokAsyncSpec {
//  val messageGen = ByteGen.genBoundedBytes(256, 256).map(x => ByteVector(x))
//
//  def check(curve: Signature) =
//    s"curve ${curve.algo}" should {
//
//      "sign and verify for right keypair" in {
//        val message = messageGen.sample.get
//        val p = for {
//          kp <- curve.generateKeyPair[IO]
//          sig <- curve.sign[IO](message, kp)
//          verify <- curve.verify[IO](message, sig, kp.public)
//          r = verify shouldBe true
//        } yield r
//
//        p.unsafeToFuture()
//      }
//
//      "not verified for wrong keypair" in {
//        val message = messageGen.sample.get
//        val p = for {
//          kp1 <- curve.generateKeyPair[IO]
//          kp2 <- curve.generateKeyPair[IO]
//          sig <- curve.sign[IO](message, kp1)
//          verify <- curve.verify[IO](message, sig, kp2.public)
//          r = verify shouldBe true
//        } yield r
//
//        p.unsafeToFuture()
//      }
//    }
//
//  check(Ed25519)
//}
