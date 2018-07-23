package jbok.evm

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._

/**
  * Entry point to executing a program.
  */
class VM {
  private[this] val log= org.log4s.getLogger
  /**
    * Executes a program
    * @param context context to be executed
    * @return result of the execution
    */
  def run[F[_]: Sync](context: ProgramContext[F]): F[ProgramResult[F]] = {
    OptionT.fromOption[F](PrecompiledContracts.runOptionally(context)).getOrElseF {
      run(ProgramState[F](context)).map { finalState =>
        ProgramResult[F](
          finalState.returnData,
          finalState.gas,
          finalState.world,
          finalState.addressesToDelete,
          finalState.logs,
          finalState.internalTxs,
          finalState.gasRefund,
          finalState.error
        )
      }
    }
  }

  private def run[F[_]: Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val byte = state.program.getByte(state.pc)
    state.config.byteToOpCode.get(byte) match {
      case Some(opCode) =>
        for {
          newState <- opCode.execute(state)
          _ = log.trace(s"$opCode | pc: ${newState.pc} | depth: ${newState.env.callDepth} | gas: ${newState.gas} | stack: ${newState.stack}")
          s <- if (newState.halted) newState.pure[F] else run(newState)
        } yield s

      case None =>
        state.withError(InvalidOpCode(byte)).halt.pure[F]
    }
  }
}

object VM extends VM


