package jbok.network

import jbok.network.json.JsonRPCError

trait JsonRpcAPI[F[_]] {
  type R[A] = F[Either[JsonRPCError, A]]
}
