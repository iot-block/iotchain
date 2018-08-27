package jbok.core.consensus

import jbok.core.models._
import scodec.bits.ByteVector

abstract class Engine[F[_]](chain: ChainReader[F]) {
  // Author retrieves the Ethereum address of the account that minted the given
  // block, which may be different from the header's coinbase if a consensus
  // engine is based on signatures.
  def author(header: BlockHeader): F[Address]

  // VerifyHeader checks whether a header conforms to the consensus rules of a
  // given engine. Verifying the seal may be done optionally here, or explicitly
  // via the VerifySeal method.
  def verifyHeader(header: BlockHeader, seal: Boolean): F[Unit]

  // VerifyHeaders is similar to VerifyHeader, but verifies a batch of headers
  // concurrently. The method returns a quit channel to abort the operations and
  // a results channel to retrieve the async verifications (the order is that of
  // the input slice).
  def verifyHeaders(headers: List[BlockHeader], seals: List[Boolean]): F[Unit]

  // VerifyUncles verifies that the given block's uncles conform to the consensus
  // rules of a given engine.
  def verifyUncles(block: Block): F[Unit]

  // VerifySeal checks whether the crypto seal on a header is valid according to
  // the consensus rules of the given engine.
  def verifySeal(header: BlockHeader): F[Unit]

  // Prepare initializes the consensus fields of a block header according to the
  // rules of a particular engine. The changes are executed inline.
  def prepare(header: BlockHeader): F[Unit]

  // Finalize runs any post-transaction state modifications (e.g. block rewards)
  // and assembles the final block.
  // Note: The block header and state database might be updated to reflect any
  // consensus rules that happen at finalization (e.g. block rewards).
  def finalize(header: BlockHeader,
               txs: List[SignedTransaction],
               uncles: List[BlockHeader],
               receipts: List[Receipt]): F[Block]

  // Seal generates a new block for the given input block with the local miner's
  // seal place on top.
  def seal(block: Block): F[Block]

  // CalcDifficulty is the difficulty adjustment algorithm. It returns the difficulty
  // that a new block should have.
  def calcDifficulty(time: Long, parent: BlockHeader): F[BigInt]

  // APIs returns the RPC APIs this consensus engine provides.
//  APIs(chain ChainReader) []rpc.API

  // Protocol returns the protocol for this consensus
//  Protocol() Protocol
}
