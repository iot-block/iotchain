package jbok.sdk.client

import java.net.URI

import cats.effect.IO
import jbok.network.client.Client
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}

import scala.annotation.meta.field
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("JbokClientClass")
case class JbokClient(@(JSExport @field) uri: URI,
                      @(JSExport @field) client: Client[IO, String],
                      @(JSExport @field) public: PublicAPI[IO],
                      @(JSExport @field) personal: PersonalAPI[IO],
                      @(JSExport @field) admin: AdminAPI[IO]) {
  @JSExport
  def status: IO[Boolean] = client.haltWhenTrue.get.map(!_)
}
