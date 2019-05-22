//package jbok.app.request
//
//import java.net.URI
//
//import cats.effect.IO
//import jbok.app.AppState
//import jbok.common.execution._
//
//
//object request {
//  def validateClient(state: AppState): IO[String] =
//    state.activeNode.value match {
//      case Some(id) if state.nodes.value.contains(id) => IO.pure(state.nodes.value(id).addr)
//      case None                                       => IO.raiseError(new Error("no selected node."))
//    }
//
//  def newAccount(state: AppState, passphrase: String): IO[String] =
//    for {
//      clientAddr <- validateClient(state)
//      client     <- jbok.app.client.JbokClient(new URI(clientAddr))
//      address    <- client.personal.newAccount(passphrase)
//    } yield address.toString
//
//}
