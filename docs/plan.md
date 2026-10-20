# Implementation plan

Milestones 1–3 are implemented. Milestones 4–7 are planned.
See [compiler integration](compiler-integration.md) for the current scope.

## Objective

Taxi is the source of truth for domain schemas. Use Taxi's compiler, then a domain
model and validated wire-layout plan, to generate Kotlin POJOs, SBE codecs, and
flyweight adapters. Do not generate intermediate SBE XML or invoke the official
SBE compiler in the production generation path.

## Milestones and acceptance criteria

1. Compiler integration: pin a compatible Taxi compiler, validate the example
   syntax and semantic primitive mappings, resolve annotations, and preserve
   source locations in diagnostics.
2. POJO generation: emit deterministic Kotlin sources under build/, compile them
   from a clean build, and reject unsupported Taxi constructs explicitly.
3. Fixed-layout codecs: generate Order with orderId, price, and quantity as three
   required int64 fields; use little endian, an eight-byte header of four uint16
   values, and a 24-byte root block. Independently verify bytes and buffer bounds.
4. Aeron IPC demo: publish an encoded Order and read it in the subscriber through
   a generated flyweight. Materialize a POJO explicitly when ownership is needed.
5. Evolution: append an optional field in version 1. Verify new readers with old
   messages and old readers with new messages against preserved layout baselines.
6. Rich types: add enums, nullable primitives, composites, groups, and variable
   data one feature at a time with explicit wire rules and compatibility tests.
7. Benchmarks: JMH comparisons of equivalent field reads, materialization, and
   encoding, reporting allocations and environment. Measure Aeron separately.

## Architecture and validation

Separate compiler integration, domain semantics, wire-layout calculation, and
code emission. Compute offsets once; let the domain flyweight delegate to the
SBE decoder. Preserve semantic type names even when Kotlin properties use Long.
Define price scale explicitly before implementing the trading example.

Round trips alone do not prove compatibility. Use independently reviewed binary
fixtures and reference-codec interoperability in addition to property checks.
Reference SBE XML, if needed for the independent oracle, belongs only in tests.
Pin the SBE specification/profile before claiming conformance.

Test nonzero offsets, truncated messages, invalid identifiers, null sentinels,
unknown enums, version mismatches, and deterministic regeneration. Treat borrowed
Aeron buffers as callback-scoped; copy data that must survive the callback.

## References

- [Taxi compiler](https://github.com/taxilang/taxilang)
- [Taxi documentation](https://taxilang.org/docs)
- [FIX SBE specification](https://fixtrading.org/packages/simple-binary-encoding-technical-specification-final/)
- [SBE versioning](https://github.com/aeron-io/simple-binary-encoding/wiki/Message-Versioning)
