package jbok.core.genesis

import jbok.core.History

trait GenesisLoader {
  def load[F[_]](history: History[F], genesis: GenesisConfig): F[Unit]
}