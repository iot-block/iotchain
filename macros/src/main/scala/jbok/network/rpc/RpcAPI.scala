package jbok.network.rpc

import cats.data.OptionT
import cats.effect.IO
import jbok.network.json.{JsonRPCError, JsonRPCResponse}

trait RpcAPI {
  type Response[+A] = IO[Either[JsonRPCError, A]]

  protected def ok[A](a: IO[A]): Response[A] = a.map(x => Right(x))

  protected def ok[A](a: A): Response[A] = IO.pure(Right(a))

  protected def ok[A](optionT: OptionT[IO, A]): Response[Option[A]] = ok(optionT.value)

  protected def error[A](error: JsonRPCError): Response[A] = IO.pure(Left(error))

  protected def error[A](e: Throwable): Response[A] = IO.pure(Left(JsonRPCResponse.internalError(e.toString)))

  protected def invalid(message: String): Response[Nothing] = IO.pure(Left(JsonRPCResponse.invalidParams(message)))
}
