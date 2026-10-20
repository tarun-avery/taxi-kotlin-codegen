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

The adapter does not calculate offsets or assign IDs. A separate PojoGenerator
now emits Kotlin data classes from this representation. SBE code is still planned.
The example's typed Message and Field annotations are metadata only at this stage.
Scalar declarations are resolved to field types, not emitted as separate domain
entities; scalar-level annotations are not propagated to fields in this milestone.

## Supported scope

One UTF-8 file can declare models, Long/String semantic types, explicit numeric
enums, composite fields, and arrays of fixed-layout models. Nullable Longs and
enums are supported in binary codecs. Typed annotations are checked by Taxi.
The adapter preserves these shapes; wire planners validate their supported layout
combinations separately. Generic object-level nullability is not supported by codecs.

Functions, services, queries, computed/constrained fields, model inheritance,
type extensions, unsupported primitives, and primitive arrays are rejected.
External package resolution is outside the current single-file CLI scope.

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
The generated-example module compiles the generated OrderPojo and tests its value
semantics. No SBE conformance or performance claim is made in this milestone.
