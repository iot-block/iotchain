package jbok.evm

import cats.effect.IO
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.ledger.History
import jbok.core.models.{Account, Address, UInt256}
import jbok.core.testkit._
import jbok.persistent.KeyValueDB
import org.scalacheck.{Arbitrary, Gen}

object testkit {
  val testStackMaxSize = 32

  def getListGen[T](minSize: Int, maxSize: Int, genT: Gen[T]): Gen[List[T]] =
    Gen.choose(minSize, maxSize).flatMap(size => Gen.listOfN(size, genT))

  val ownerAddr   = Address(0x123456)
  val callerAddr  = Address(0xabcdef)
  val receiveAddr = Address(0x654321)

  implicit def arbProgram: Arbitrary[Program] = Arbitrary {
    for {
      code <- genBoundedByteVector(0, 256)
    } yield Program(code)
  }

  implicit def arbExecEnv: Arbitrary[ExecEnv] = Arbitrary {
    for {
      gasPrice  <- arbUint256.arbitrary
      inputData <- genBoundedByteVector(0, 256)
      value     <- arbUint256.arbitrary
      program   <- arbProgram.arbitrary
      header    <- arbBlockHeader.arbitrary
      callDepth <- intGen(0, 1024)
    } yield ExecEnv(ownerAddr, callerAddr, callerAddr, gasPrice, inputData, value, program, header, callDepth)
  }

  implicit def arbProgramContext(evmConfig: EvmConfig): Arbitrary[ProgramContext[IO]] = Arbitrary {
    for {
      env   <- arbExecEnv.arbitrary
      gas   <- uint256Gen(UInt256.MaxValue, UInt256.MaxValue)
      world <- arbWorldState(env).arbitrary
    } yield ProgramContext(env, receiveAddr, gas, world, evmConfig)
  }

  implicit def arbWorldState(env: ExecEnv): Arbitrary[WorldState[IO]] = Arbitrary {
    import CoreSpec.chainId
    import CoreSpec.timer
    import CoreSpec.metrics
    val history = History.forBackendAndPath[IO](KeyValueDB.INMEM, "").unsafeRunSync()

    history
      .getWorldState()
      .unsafeRunSync()
      .putCode(env.ownerAddr, env.program.code)
      .putAccount(env.ownerAddr, Account.empty().increaseBalance(env.value))
      .persisted
      .unsafeRunSync()
  }

  implicit def arbMemory: Arbitrary[Memory] = Arbitrary {
    for {
      bv <- genBoundedByteVector(0, 256)
    } yield Memory.empty.store(0, bv)
  }

  implicit def arbStack(nElems: Int, elemGen: Gen[UInt256]): Arbitrary[Stack] = Arbitrary {
    for {
      list <- Gen.listOfN(nElems, elemGen)
      stack = Stack.empty(testStackMaxSize)
    } yield stack.push(list)
  }

  implicit def arbProgramState(implicit evmConfig: EvmConfig): Arbitrary[ProgramState[IO]] = Arbitrary {
    for {
      context <- arbProgramContext(evmConfig).arbitrary
    } yield ProgramState(context)
  }
}
