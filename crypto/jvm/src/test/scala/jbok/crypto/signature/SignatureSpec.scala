//package jbok.crypto.signature
//
//import jbok.JbokSpec
//import jbok.crypto._
//import scodec.Codec
//
//class SignatureSpec extends JbokSpec {
//  "signature" should {
//
//    val S = Signature[Curves.secp256k1]
//
//    "generate keys and encode and decode" in {
//      forAll { s: String =>
//        val toSign = s.getBytes
//        val keypair = S.generateKeyPair
//        val signed = S.sign(toSign, keypair.priKey)
//        val decodedSignature = Codec.decode[Sig](signed.bits).require.value
//
//        val decodedPubKey = Codec.decode[PubKey](keypair.pubKey.bits).require.value
//        val verify = S.verify(toSign, decodedSignature, decodedPubKey)
//        verify shouldBe true
//      }
//    }
//
//    "have same keys" in {
//      forAll { s: String =>
//        val toSign = s.getBytes
//        val keypair = S.generateKeyPair
//        val signed = S.sign(toSign, keypair.priKey)
//        val verify = S.verify(toSign, signed, keypair.pubKey)
//        verify shouldBe true
//      }
//    }
//
//    "have different keys" in {
//      forAll { s: String =>
//        val toSign = s.getBytes
//        val keypair1 = S.generateKeyPair
//        val keypair2 = S.generateKeyPair
//        val signed = S.sign(toSign, keypair1.priKey)
//        val verify = S.verify(toSign, signed, keypair2.pubKey)
//        verify shouldBe false
//      }
//    }
//  }
//}
