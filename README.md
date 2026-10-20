# Taxi to Kotlin Code Generator

A learning project to generate Kotlin domain objects, buffer-backed flyweights,
and SBE-compatible codecs from Taxi schemas. The aim is to compare ownership,
allocation, serialization, and schema evolution using the same domain definition.

**Status:** milestones 1–5 implemented. Taxi generates Kotlin data classes,
fixed-layout SBE encoders/decoders, and buffer-backed flyweights. The build
compiles the generated Order example and checks its binary fixture. The Aeron IPC demo and bidirectional version 0/1 compatibility tests run in the
build. Richer types and benchmarks are still planned.

## Getting started

Use JDK 25 and the included Gradle wrapper. Internet access is needed for the
first dependency download. Open this directory in IntelliJ IDEA as a Gradle
project and select JDK 25 for the Gradle JVM.

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
file, line, and column. The tool reads the schema without modifying it and writes sources only when `--output` is supplied. The first build downloads Taxi from its official Orbital
release repository as well as dependencies from Maven Central.

Only simple Long fields (including semantic subtypes and nullable fields) are
currently supported. POJO generation preserves nullability; the SBE profile supports required and nullable
Long fields. Message/field IDs and fixed layouts are validated. See
[compiler integration](docs/compiler-integration.md) for scope and diagnostics.

See [the implementation plan](docs/plan.md) for milestones and the intended
Order example. Contributors should read [AGENTS.md](AGENTS.md), reproduce the
build, and follow its milestone commit workflow. For project
questions or proposed changes, open a repository issue.

Generated example sources live under `generated-example/build/generated/sources/taxi/main/kotlin`.
The `generatePojos` task (now also generating codecs) replaces that generated directory when its inputs change;
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
