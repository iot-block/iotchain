package jbok.network

package object client {
  type Client[F[_], A] = Connection[F, A]
}
