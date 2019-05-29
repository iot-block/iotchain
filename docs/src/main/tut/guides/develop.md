---
layout: docsplus
title:  "Develop Guide"
number: 1
---

### Getting Started

*dependencies*

- java 1.8+
- scala 2.12.8
- sbt 1.2.8
- node
- yarn

*libraries*
- `cats` and `cats-effect`
- `fs2`
- `scalajs`

We also provide a simple script in `bin/install-deps.sh` to download and install
dependencies automatically.

### Build

build all
```
$ sbt compile
```

build a sub project and its dependencies
```
$ sbt ";project coreJVM; commpile"
```

build docs
```
$ sbt
> project docs
> makeMicrosite
```

view docs locally
```
cd docs/target/site
jekyll serve
```
open http://localhost:4000/jbok/

### Tests

**run tests**

run all tests
```
$ sbt test
```

run tests of a sub project
```
$ sbt ";project <project>; test"

# e.g.
$ sbt ";project coreJVM; test"
$ sbt ";project networkJS; test"
```

run some specified tests
```
$ sbt testOnly jbok.evm.*
```

**write tests**

We use `ScalaTest` to write tests.

There are two `CommonSpec` traits for JVM and JS respectively:
- `common/js/src/test/scala/jbok/common/CommonSpec`
- `common/jvm/src/test/scala/jbok/common/CommonSpec`

For more examples, please find existing specs in `src/test`.
