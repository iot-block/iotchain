package jbok.crypto.authds.merkle

import jbok.JbokSpec
import jbok.crypto._
import jbok.crypto.hashing.Hashing
import org.scalacheck._

class MerkleSpec extends JbokSpec {
  "merkle tree" should {
    "provide merkle proof" in {
      implicit val hasher = Hashing.sha256

      val N = 100
      val stringSet = Gen.listOfN(N, Gen.alphaStr).sample.get.toSet
      val gen: Gen[(List[String], List[String])] = for {
        out <- Gen.listOfN(N, Gen.alphaStr.suchThat(x => !stringSet.contains(x)))
        in = stringSet.toList
      } yield (in, out)

      forAll(gen) {
        case (in, out) =>
          val hashes = in.map(_.utf8bytes.digested(hasher))
          val tree = MerkleTree(hashes)

          in.foreach(h => tree.provideProof(h.utf8bytes.digested(hasher)).map(_.isValid(tree.rootHash)) shouldBe Some(true))
          out.foreach(h => tree.provideProof(h.utf8bytes.digested(hasher)).map(_.isValid(tree.rootHash)) shouldBe None)
      }
    }
  }
}
