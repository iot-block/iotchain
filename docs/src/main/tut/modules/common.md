---
layout: docsplus
title:  "Common"
number: 2
---

`Common` module provides some useful utils:

## shared

- `common/shared/src/main/scala/jbok/common/log` is a wrapper of `scribe` logger, add a `cats.effect.Sync` basically.
- `common/shared/src/main/scala/jbok/common/metrics` contains a general `Metrics` interface and syntax. The default backend now is `Prometheus`.

## jvm specific

- `common/jvm/src/main/scala/jbok/common/FileUtil` it should implements all common file operations(e.g. open, read, write, remove, lock, temporaryFile, etc.).
- `common/jvm/src/main/scala/jbok/common/config` provides a common entry for reading and dumping config file from `String`, `*.json` and `*.yaml`.
