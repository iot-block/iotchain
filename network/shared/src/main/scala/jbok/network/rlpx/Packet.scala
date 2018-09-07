package jbok.network.rlpx

import scodec.bits.ByteVector

/**
  * header: frame-size || header-data || padding
  * frame-size: 3-byte integer size of frame, big endian encoded (excludes padding)
  *
  * header-data:
  *   normal: rlp.list(protocol-type[, context-id])
  *   chunked-0: rlp.list(protocol-type, context-id, total-packet-size)
  *   chunked-n: rlp.list(protocol-type, context-id)
  *   values:
  *     protocol-type: < 2**16
  *     context-id: < 2**16 (optional for normal frames)
  *     total-packet-size: < 2**32
  *
  * padding: zero-fill to 16-byte boundary
  */
case class Header(frameSize: Int, protocolType: Int, contextId: Option[Int], totalPacketSize: Option[Int])

/**
  * Single-frame packet:
  * header || header-mac || frame || frame-mac
  *
  * Multi-frame packet:
  * header || header-mac || frame-0 ||
  * [ header || header-mac || frame-n || ... || ]
  * header || header-mac || frame-last || frame-mac
  *
  * header-mac: right128 of egress-mac.update(aes(mac-secret,egress-mac) ^^ header-ciphertext).digest
  *
  * frame:
  *   normal: rlp(packet-type) [|| rlp(packet-data)] || padding
  *   chunked-0: rlp(packet-type) || rlp(packet-data...)
  *   chunked-n: rlp(...packet-data) || padding
  *
  * padding: zero-fill to 16-byte boundary (only necessary for last frame)
  *
  * frame-mac: right128 of egress-mac.update(aes(mac-secret,egress-mac) ^^ right128(egress-mac.update(frame-ciphertext).digest))
  *
  * egress-mac: h256, continuously updated with egress-bytes*
  * ingress-mac: h256, continuously updated with ingress-bytes*
  */
case class Packet(header: Header, packetType: Int, payload: ByteVector)
