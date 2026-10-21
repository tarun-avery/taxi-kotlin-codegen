# Taxi to Kotlin Code Generator

A learning project to generate Kotlin domain objects, buffer-backed flyweights,
and SBE-compatible codecs from Taxi schemas. The aim is to compare ownership,
allocation, serialization, and schema evolution using the same domain definition.

**Status:** milestones 1–7 implemented within the supported subset. Taxi generates Kotlin data classes,
fixed-layout SBE encoders/decoders, and buffer-backed flyweights. The build
compiles the generated Order example and checks its binary fixture. The Aeron IPC demo and bidirectional version 0/1 compatibility tests run in the
build. Enums, fixed composites, fixed-entry groups, and UTF-8 data are supported;
official SBE reference-codec interoperability checks and JMH benchmarks are included.
See [benchmark results and limitations](docs/benchmarks.md).

## Getting started

Use JDK 25 and the included Gradle wrapper. Internet access is needed for the
first dependency download. Open this directory in IntelliJ IDEA as a Gradle
project and select JDK 25 for the Gradle JVM. Kotlin is pinned to 2.2.21
for compatibility with IntelliJ IDEA 2025.2.4. Java and Kotlin emit JVM 24
bytecode while using JDK 25 to build and run. After changing build configuration,
use IntelliJ’s **Sync Gradle Changes** action to refresh its dependencies.

From the project root:

```sh
./gradlew run --args=examples/order.taxi
./gradlew generatePojos
./gradlew :generated-example:run
./gradlew clean build
./gradlew ktlintFormat
./gradlew ktlintCheck
```

The example prints `model trading.Order`, its message annotation, and three
resolved fields, including `orderId: trading.OrderId -> LONG`. Prices use integer
units of one ten-thousandth of a currency unit.

`build` runs the adapter tests and formatting checks. Compilation errors include
file, line, and column. The tool reads the schema without modifying it and writes sources only when `--output` or `--codecs` is supplied. The first build downloads Taxi from its official Orbital
release repository as well as dependencies from Maven Central.

Supported data includes Long fields and semantic subtypes, nullable Longs/enums,
explicit numeric enums, fixed composites, fixed-entry groups, and UTF-8 strings.
The rich-codec profile is version 0 only; fixed Long messages support append-only
schema evolution. Unsupported constructs fail explicitly. See
[compiler integration](docs/compiler-integration.md) and [wire profile](docs/wire-profile.md).

See [the implementation plan](docs/plan.md) for milestones and the intended
Order example. Contributors should read [AGENTS.md](AGENTS.md), reproduce the
build, and follow its milestone commit workflow. For project
questions or proposed changes, open a repository issue.

Generated example sources live under `generated-example/build/generated/sources/taxi/main/kotlin`.
The `generatePojos`, `generateEvolution`, and `generateRich` tasks replace their generated directories when its inputs change;
never edit it by hand. `clean` removes build output. The CLI also accepts
`--output <directory>` to write sources elsewhere; it overwrites matching generated
filenames but does not remove stale files. Kotlin keywords are escaped, nullable
Long fields become `Long?`, and unsupported names or empty data classes fail explicitly.

See [the wire profile](docs/wire-profile.md) for the supported SBE subset and APIs.

The IPC demo prints `Received through Aeron IPC: OrderPojo(orderId=123, price=100500, quantity=10)`.
It launches an embedded driver in a unique system temporary directory, uses IPC
only (no network port), and closes resources/removes that directory on exit.
Each send/receive attempt has a ten-second deadline. Tests include a real IPC
exchange. No external Aeron driver or configuration is required.
