---
layout: home

---

# IotChain
[![Build Status](https://travis-ci.org/iot-block/iotchain.svg?branch=master)](https://travis-ci.org/iot-block/iotchain)


## Quick Start

### how to build
**required**

install `jdk1.8` and `scala 2.12+`

**enable the `SBT` plugin** 

optional but recommended

adding
```bash
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.3")
```
to `~/.sbt/1.0/plugins/build.sbt` (create if not exist)  

**compile and test**
```bash
sbt> compile
sbt> test 
```

Note that the build contains multiple sub projects and is cross compiled against multiple platforms, use

```bash
sbt> project <project>JVM
sbt> project <project>JS
```
to choose specified project on `JVM` or `JavaScript`

### Credits

Inspired by the following projects:
- [Ethereum](https://github.com/ethereum/go-ethereum)
- [Mantis](https://github.com/input-output-hk/mantis)
- [Scorex](https://github.com/ScorexFoundation/Scorex)
- etc.

and great libraries:
- [cats-effect](https://github.com/typelevel/cats-effect)
- [fs2](https://github.com/functional-streams-for-scala/fs2)
- [http4s](https://github.com/http4s/http4s)
- [tsec](https://github.com/jmcardon/tsec)
- etc.

### License
```
MIT License

Copyright (c) 2018 - 2019 The IotChain Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
