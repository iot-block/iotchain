package jbok.evm

import cats.effect.IO
import jbok.common.math.N
import jbok.core.models.UInt256
import jbok.core.models.UInt256._

class OpCodeGasSpecConstantinople extends OpCodeSpec {
  implicit override val evmConfig: EvmConfig = EvmConfig.ConstantinopleConfigBuilder(None)

  test(SSTORE) { op =>
    val table = Table[N, N, UInt256, UInt256, UInt256](
      ("expectedGas", "expectedRefund", "original", "current", "value"),
      (200, 0, 0, 0, 0),
      (20000, 0, 0, 0, 1),
      (200, 19800, 0, 1, 0),
      (200, 0, 0, 1, 2),
      (200, 0, 0, 1, 1),
      (200, 0, 1, 0, 0),
      (200, -15000, 1, 0, 1),
      (200, -15000, 1, 0, 2),
      (200, 15000, 1, 2, 0),
      (200, 0, 1, 2, 3),
      (200, 4800, 1, 2, 1),
      (200, 0, 1, 2, 2),
      (5000, 15000, 1, 1, 0),
      (5000, 0, 1, 1, 2),
      (200, 0, 1, 1, 1)
    )
    val offset = UInt256.zero

    forAll(table) { (expectedGas, expectedRefund, original, current, value) =>
      val state      = random[ProgramState[IO]]
      val ownAddress = state.ownAddress

      val originalStorage = state.world
        .getStorage(ownAddress)
        .unsafeRunSync()
        .store(offset, original)
        .unsafeRunSync()
      val originalWorld = state.world.putStorage(ownAddress, originalStorage).persisted.unsafeRunSync()
      val currentStorage = originalWorld
        .getStorage(ownAddress)
        .unsafeRunSync()
        .store(offset, current)
        .unsafeRunSync()
      val stackIn = Stack.empty().push(value).push(offset)

      val stateIn =
        state.withStack(stackIn).withWorld(originalWorld).withStorage(currentStorage).copy(gas = expectedGas)
      val stateOut = op.execute(stateIn).unsafeRunSync()
      verifyGas(expectedGas, stateIn, stateOut, allowOOG = false)
      stateOut.gasRefund shouldBe expectedRefund
    }
  }
}
