# Benchmarks and independent compatibility checks

Milestone 7 adds JMH 1.37 in a separate Java harness, with annotation processing,
forked JVMs, consumed results, and the GC profiler. It measures the generated
fixed Order codec; it does not characterize every supported schema.

## Reproduce

Use JDK 25 and run from the repository root. Run benchmarks without builds or
other heavy work running concurrently. The following short exploratory run was
used for the recorded results:

```sh
./gradlew clean build
./gradlew :benchmarks:run --args='OrderBenchmark -wi 2 -i 3 -w 500ms -r 500ms -f 2 -prof gc -rf json -rff benchmarks/build/reports/jmh/order.json'
./gradlew :benchmarks:run --args='AeronLifecycleBenchmark -prof gc -rf json -rff benchmarks/build/reports/jmh/aeron.json'
```

The codec run uses one thread, two forks, two 500 ms warmup iterations and three
500 ms measurement iterations per fork for each of average time and throughput.
Its heap is fixed at 256 MiB. Omitting command-line timing options uses the
annotations' longer three 1 s warmups and five 1 s measurements. The Aeron run
uses one fork, one 1 s warmup and three 1 s measurements, with JVM-default heap
sizing. Both fork configurations export `jdk.internal.misc` for Agrona on JDK 25.
Raw JSON lives under `benchmarks/build/reports/jmh/`; `clean` removes it.

## Meaning of each operation

- `existingPojoRead`: sum three fields of an already owned object. This is a
  reference baseline, with no decoding, header validation, or ownership transfer.
- `decoderRead`: validate and wrap an existing 32-byte message, then sum its three
  fields using a reused decoder.
- `flyweightRead`: perform the same wrap and sum through the generated domain view.
- `materializeAndRead`: wrap, create an owned POJO, force it to escape through a
  JMH Blackhole, and sum the same fields. Escape is deliberate: a local object
  that never escapes might be removed by the JIT.
- `encode`: write the header and three fields into a reused buffer, returning a
  read-back value. It is a serialization workload, separate from the read cases.
- `isolatedDriverExchange`: create an isolated embedded Aeron driver and client,
  connect a publication/subscription, encode/send/read one Order, copy the result,
  close resources and remove the temporary directory. This measures the whole
  demonstration lifecycle, **not steady-state IPC latency or throughput**.

Setup allocates the codec buffers and reused views outside the measured operation.
The codec benchmark uses heap-backed UnsafeBuffer storage and one small repeated
message; results do not establish direct-buffer, cache-miss, concurrent, rich-type,
or network performance. The GC profiler measures allocation across an iteration;
near-zero values include measurement overhead and are not a universal zero-allocation
guarantee. Short runs and a developer laptop provide exploratory evidence only.

## Recorded exploratory results

Measured 20 October 2026 on Apple M1, macOS 27.0.1, Homebrew OpenJDK
64-Bit Server VM 25.0.2, JMH 1.37, Agrona 2.7.0, and Aeron 1.53.3.
Values below are JMH means; ± is its reported 99.9% confidence error, not a
latency percentile. Throughput is from separate runs, converted from ops/ns to
million operations/second. Allocation is from average-time runs.

| Operation | ns/op ± error | Million ops/s ± error | Bytes/op |
| --- | ---: | ---: | ---: |
| `existingPojoRead` | 0.707 ± 0.043 | 1430.1 ± 79.3 | 0.000 |
| `decoderRead` | 4.899 ± 0.017 | 201.2 ± 19.2 | 0.000 |
| `flyweightRead` | 4.890 ± 0.030 | 203.8 ± 2.0 | 0.000 |
| `materializeAndRead` | 5.340 ± 0.021 | 185.5 ± 6.7 | 40.000 |
| `encode` | 3.526 ± 0.118 | 285.0 ± 1.5 | 0.000 |

Rounded 0.000 B/op entries had measured values below 0.0001 B/op.
The view and decoder results are similar in this workload; materialization
allocated approximately 40 B/op. Existing-POJO access is cheaper but presupposes
that decoding and ownership have already been handled. These measurements do
not justify a general performance ranking outside this workload.

The separate Aeron **whole lifecycle** measured 73.842 ± 37.629 ms/op
and 87776 ± 157746 B/op. Its very wide uncertainty and inclusion of
startup, connection, polling waits, materialization, and cleanup make it unsuitable
for inferring transport latency. A future steady-state harness should hold the
driver/publication/subscription open, define backpressure and idle strategies,
and report latency distributions separately from throughput.

## Independent SBE oracle

`generated-example/src/test/resources/reference.xml` is a handwritten test schema.
The build invokes pinned official SBE tool 1.41.0 solely to generate Java test
codecs. Tests encode using each implementation, compare bytes, and decode with
the other at a nonzero offset. Cases cover fixed Order and Trade's optional int64,
optional enum, composite, fixed-entry group, and UTF-8 data, including empty/null
values. Existing independent byte fixtures, truncation tests, and fixed Long
schema-evolution tests remain in place. This demonstrates interoperability for
these cases, not certification of the complete SBE specification. The production
Taxi generation pipeline does not use XML or the official SBE generator.
