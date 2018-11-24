
> "Trust no one, bench everything."

**basic usage**

`> jmh:run .*<Benchmark>.*`

see more [examples](https://github.com/ktoso/sbt-jmh/tree/master/plugin/src/sbt-test/sbt-jmh/run/src/main/scala/org/openjdk/jmh/samples)

**profile**

**install FlameGraph**

`git clone https://github.com/brendangregg/FlameGraph`

**install AsyncProfiler**

download release at []()https://github.com/jvm-profiling-tools/async-profiler)

**run**

`> jmh:run -prof jmh.extras.Async:dir=/tmp/profile-async;asyncProfilerDir=<AsyncProfilerDir>;flameGraphDir=<FlameGraphDir>;flameGraphOpts=--minwidth,2;verbose=true .*<BenchMarkName>.*`
