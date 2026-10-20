# Taxi to Kotlin Code Generator

A learning project to generate Kotlin domain objects, buffer-backed flyweights,
and SBE-compatible codecs from Taxi schemas. The aim is to compare ownership,
allocation, serialization, and schema evolution using the same domain definition.

**Status:** milestone 1 implemented. The command-line tool compiles one Taxi file
into an internal domain model and prints resolved fields and annotations. Kotlin
source generation, SBE codecs, the Aeron demo, and benchmarks are still planned.

## Getting started

Use JDK 25 and the included Gradle wrapper. Internet access is needed for the
first dependency download. Open this directory in IntelliJ IDEA as a Gradle
project and select JDK 25 for the Gradle JVM.

From the project root:

```sh
./gradlew run --args=examples/order.taxi
./gradlew clean build
./gradlew ktlintFormat
./gradlew ktlintCheck
```

The example prints `model trading.Order`, its message annotation, and three
resolved fields, including `orderId: trading.OrderId -> LONG`. Prices use integer
units of one ten-thousandth of a currency unit.

`build` runs the adapter tests and formatting checks. Compilation errors include
file, line, and column. The tool reads the schema without modifying it and does
not generate files yet. The first build downloads Taxi from its official Orbital
release repository as well as dependencies from Maven Central.

Only simple Long fields (including semantic subtypes and nullable fields) are
currently supported. Annotation values are retained as metadata; SBE identifier,
null encoding, and layout validation are not implemented yet. See
[compiler integration](docs/compiler-integration.md) for scope and diagnostics.

See [the implementation plan](docs/plan.md) for milestones and the intended
Order example. Contributors should read [AGENTS.md](AGENTS.md), reproduce the
build, and present changes for review before committing or pushing. For project
questions or proposed changes, open a repository issue.
