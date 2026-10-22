# Taxi to Kotlin Code Generator

Generate Kotlin domain objects, buffer-backed flyweights, and SBE-compatible
encoders and decoders from Taxi schemas. A shared definition keeps domain meaning
and binary layouts together, and supports comparisons of ownership, allocation,
serialization, and schema evolution.

**Status:** version `0.2.0`; milestones 1–7 are implemented within the supported
subset. Generated examples compile in the build. Tests cover Aeron IPC, fixed-field
version 0/1 evolution, independently specified bytes, and interoperability with
reference SBE codecs. JMH benchmarks are included; see [results and limitations](docs/benchmarks.md).
The cluster-state-machine project consumes this generator at build time to encode
add, subtract, multiply, and divide commands and their results.

## Requirements and setup

Use JDK 25, Git, and the included Gradle wrapper. No separate Gradle installation
is needed. The first build downloads dependencies from Maven Central and Taxi's
Orbital release repository. In IntelliJ, import the root Gradle project with JDK 25.
Kotlin is pinned to 2.2.21; generator Java/Kotlin bytecode targets JVM 24.

From the repository root:

```sh
./gradlew :run --args=examples/order.taxi
./gradlew generatePojos
./gradlew :generated-example:run
```

The first command prints `model trading.Order` and resolved semantic fields,
including `orderId: trading.OrderId -> LONG`. The IPC example prints:

```text
Received through Aeron IPC: OrderPojo(orderId=123, price=100500, quantity=10)
```

The IPC example creates a temporary embedded driver, needs no external service,
and closes its resources/removes temporary data on exit. Each exchange has a
ten-second deadline. Prices use integer ten-thousandths of a currency unit.
Use `:run` for the generator CLI so Gradle does not also select other projects'
application tasks.

## Generate and check schemas

```sh
./gradlew :run --args="examples/order.taxi --codecs build/generated/order"
./gradlew :run --args="examples/order-v1.taxi --check-compatible examples/order.taxi"
./gradlew generateFullRange
```

`--codecs` writes POJOs, encoders, decoders, and flyweights. `--output` writes only
POJOs. The CLI reads the source without changing it; output options overwrite
matching generated files but do not delete stale files. `--check-compatible`
compares a current fixed-profile schema against a preserved baseline and fails on
incompatible changes. Diagnostics include source file, line, and column.

The supported subset includes semantic Long types, nullable Longs/enums, explicit
numeric enums, fixed composites, fixed-entry groups, and UTF-8 data. Fixed Long
messages support append-only optional-field evolution. The rich profile is version
0 only. Unsupported constructs fail explicitly.

Ordinary Long fields reserve `Long.MIN_VALUE` as null. A required fixed-profile
field annotated `@FullRange` instead uses a nine-byte composite to preserve every
signed 64-bit value. This is how the counter's quantities and counts retain their
full range. Nullable full-range fields and rich-profile full-range fields are
unsupported. Semantic Long types currently generate plain Kotlin `Long`
properties, not distinct Kotlin value classes.

See [compiler integration](docs/compiler-integration.md) and the
[wire profile and codec APIs](docs/wire-profile.md) for precise encoding,
buffer lifetimes, and evolution rules.

## Cluster integration

Keep this repository next to `cluster-state-machine`, or configure that consumer
with `-PtaxiCodegenDir=/path/to/taxi-kotlin-codegen`. The consumer's
`gradle.properties` pins this generator's version and exact Git revision. Its
build rejects changed generator build inputs; README and `docs/` edits are allowed.
Generated counter sources remain under the consumer's `build/` directory. The
Taxi compiler and generator are not runtime dependencies of the cluster or gateway.

To regenerate the integrated domain and verify compatibility, run from the cluster
repository root:

```sh
./gradlew :counter-protocol:generateCounterCodecs
./gradlew :counter-protocol:checkCounterSchema
./gradlew :cluster:integrationTest
```

The consumer owns the counter schema and its preserved baseline. Its README and
`docs/taxi-integration.md` describe starting the cluster, gateway, and browser UI.
This generator is a build-time tool and does not need a separate running server.

## Verification and generated data

```sh
./gradlew clean build
./gradlew ktlintFormat
./gradlew ktlintCheck
```

The build compiles generated examples, runs tests, and checks Kotlin formatting.
It assembles benchmark applications but does not run JMH measurements. Generated
sources are excluded from formatter mutation and are never committed.

`generatePojos`, `generateEvolution`, `generateRich`, and `generateFullRange`
replace their own generated directories under `generated-example/build/` when
inputs change. Do not edit generated sources. `clean` removes build output;
example Taxi schemas and preserved baselines remain intact. Buffer views borrow
storage; materialize a POJO when data must outlive its buffer callback.

For design details, see [the implementation plan](docs/plan.md). For contributions,
read [AGENTS.md](AGENTS.md) and run the checks above. Use the repository's issue
tracker for questions or bugs, including the schema, diagnostic, and JDK version.
