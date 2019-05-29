---
layout: docsplus
title:  "Codec"
number: 3
---

`Codec` module contains the encoding and decoding support for json & binary.

All codecs support JVM and ScalaJS.

## json

`circe` is used for all json codec, `codec/shared/src/main/scala/jbok/codec/json` contains some
custom instances.

For some primary types such as `BigInt`, we always delegate it to `String`, rather than `circe`'s default behavior.

Some instances for core models(e.g. `Address`, `Uint256`) are defined in their companion objects.

We prefer `@ConfiguredJsonCodec` annotation, so import `jbok.codec.json.implicits._` is a must.

## binary

`RlpCodec` is used for core models binary codec.

`rlp` is a low level, unoptimized binary codec protocol, but it's behavior is concise and predictable.

It's used to encode models like `Block` or `Transaction`, for computing stable message hashes.

`RlpCodec` is implemented based on `scodec` and `magnolia`(macros for generating instances for case classes)

Theoretically, we should use some high level binary codec protocols such as `protobuf`, `avro`, etc, for objects serialization over the wire or kv-store persistence.
