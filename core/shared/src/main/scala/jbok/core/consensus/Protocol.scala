package jbok.core.consensus

case class Protocol(
    name: String, // Official short name of the protocol used during capability negotiation.
    versions: List[Int], // Supported versions of the eth protocol (first is primary).
    length: List[Long] // Number of implemented message corresponding to different protocol versions.
)
