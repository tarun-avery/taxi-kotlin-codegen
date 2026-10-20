# Taxi to Kotlin Code Generator

A learning project to generate Kotlin domain objects, buffer-backed flyweights,
and SBE-compatible codecs from Taxi schemas. The aim is to compare ownership,
allocation, serialization, and schema evolution using the same domain definition.

**Status:** milestones 1–2 implemented. The tool compiles Taxi into an internal
domain model and generates Kotlin data classes. The build generates and compiles
the Order example automatically. SBE codecs, Aeron, and benchmarks are planned.

## Getting started

Use JDK 25 and the included Gradle wrapper. Internet access is needed for the
first dependency download. Open this directory in IntelliJ IDEA as a Gradle
project and select JDK 25 for the Gradle JVM.

From the project root:

```sh
./gradlew run --args=examples/order.taxi
./gradlew generatePojos
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
currently supported. Annotation values are retained as metadata; SBE identifier,
null encoding, and layout validation are not implemented yet. See
[compiler integration](docs/compiler-integration.md) for scope and diagnostics.

See [the implementation plan](docs/plan.md) for milestones and the intended
Order example. Contributors should read [AGENTS.md](AGENTS.md), reproduce the
build, and follow its milestone commit workflow. For project
questions or proposed changes, open a repository issue.

Generated example sources live under `generated-example/build/generated/sources/taxi/main/kotlin`.
The `generatePojos` task replaces that generated directory when its inputs change;
never edit it by hand. `clean` removes build output. The CLI also accepts
`--output <directory>` to write sources elsewhere; it overwrites matching generated
filenames but does not remove stale files. Kotlin keywords are escaped, nullable
Long fields become `Long?`, and unsupported names or empty data classes fail explicitly.
