---
layout: home

---

# JBOK - Just a Bunch Of Keys
[![Build Status](https://travis-ci.com/c-block/jbok.svg?branch=master)](https://travis-ci.com/c-block/jbok)


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
