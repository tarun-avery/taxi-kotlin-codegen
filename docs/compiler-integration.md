# Compiler integration (milestone 1)

The application uses org.taxilang:compiler:1.73.0 from Taxi's official release
repository. Kotlin is pinned to 2.4.21 and the project uses JDK 25. Compiler API
choices were checked against the published 1.73.0 source artifacts.

## Data flow

The CLI reads one UTF-8 Taxi file and passes its text and source name to
TaxiCompilerAdapter. Taxi performs parsing, name resolution, annotation validation,
and type checking. The adapter translates the result into our DomainSchema,
DomainMessage, and DomainField classes; these have no Taxi compiler dependencies.

Messages are sorted by qualified name. Fields retain declaration order, which must
not later be confused with field-ID ordering. Each field retains its qualified
semantic type name, Long primitive mapping, nullability, annotations, and source
position. Message annotations and positions are also retained. Annotation parameter
keys are sorted. Built-in Taxi declarations are excluded from the domain output.

The adapter does not calculate offsets, assign IDs, or generate Kotlin/SBE code.
The example's typed Message and Field annotations are metadata only at this stage.
Scalar declarations are resolved to field types, not emitted as separate domain
entities; scalar-level annotations are not propagated to fields in this milestone.

## Supported scope

- One input file with namespaces, simple domain models, and Long fields.
- Semantic scalar subtypes of Long, including chains of inheritance.
- Nullable fields, represented as metadata without a wire null encoding yet.
- Model and field annotations, validated by Taxi when annotation types are declared.

Unsupported primitives, arrays, enums, nested object fields, model inheritance,
computed/constrained fields, type extensions, functions, services, and queries are
rejected. Broad Taxi language support is not claimed. Imports requiring external
schema files and package resolution are outside the CLI's current single-file scope.

## Diagnostics and CLI

Run `./gradlew run --args=examples/order.taxi` from the project root. CLI exit codes
are 0 for successful translation, 1 for compiler/adapter errors, and 2 for invalid
arguments or unreadable files. Gradle itself reports a task failure for nonzero
application exits. Diagnostics use one-based line and column numbers; whole-document
unsupported-feature errors use line 1, column 1. A failed translation returns no
partial DomainSchema. Warnings are printed to stderr alongside errors.

JDK 25 may print Unsafe deprecation warnings from upstream compiler/formatter
libraries. These are distinct from schema diagnostics; they are not suppressed.

## Verification

`./gradlew clean build` runs tests and ktlint checks. Tests exercise the actual Taxi
compiler, the example schema, semantic inheritance, nullability, annotations,
source diagnostics, unsupported constructs, deterministic ordering, and CLI usage.
No SBE conformance or performance claim is made in this milestone.
