package jbok.network

package object client {
  type Client[F[_]] = Connection[F]
}
