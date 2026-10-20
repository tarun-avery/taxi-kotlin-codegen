# SBE wire profile

This project implements a deliberately limited profile of FIX SBE 1.0 with the
November 2020 errata. It does not claim complete SBE feature support.

All integers are little endian. The message header contains four uint16 fields:
root block length, template ID, schema ID, and schema version. The header is eight
bytes and is excluded from the root block length. IDs and versions use 0..65534;
65535 is reserved. Fields use signed int64, with Long.MIN_VALUE reserved as null.
Nullable fields encode null using that sentinel. Required fields reject it, and
callers cannot pass the sentinel as a non-null value. Fields follow declaration order; field IDs do not determine offsets.

Order uses template 1, schema 1, version 0, with a 24-byte root block. Its body
contains orderId at offset 0, price at 8, and quantity at 16. The reviewed fixture
for (123, 100500, 10), including header, is:

```text
18000100010000007b0000000000000094880100000000000a00000000000000
```

Tests check these bytes, read them using a separate JDK ByteBuffer implementation,
and verify that mutations appear through the borrowed flyweight but not through
an already materialized POJO. This is fixed-profile evidence, not a general SBE
conformance certification.

## APIs

OrderEncoder.wrap(buffer, bodyOffset) writes a body; wrapAndApplyHeader writes
header and body. Set all required fields before publication, or use fromPojo.
OrderDecoder.wrap accepts body offset, acting block length, and acting version.
wrapAndApplyHeader additionally validates message identity and the supplied
transport message length. The caller must supply that actual length, not merely
the capacity of the surrounding receive buffer.

OrderFlyweight.wrap expects a header-bearing message. Its property getters reuse
OrderDecoder. toPojo explicitly allocates owned data. Views are not thread-safe
and must not outlive their buffers. No hidden copies extend buffer lifetime.

The build generates sources through --codecs; --output remains POJO-only.
Generated code is compiled and behavior-tested, excluded from formatter mutation,
and never committed. Agrona is pinned to 2.7.0. On JDK 25 its consumers need
--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED, configured for Gradle tests.

[FIX SBE specification](https://fixtrading.org/packages/simple-binary-encoding-technical-specification-final/)

## Schema evolution

`examples/order.taxi` is the preserved version 0 baseline. `examples/order-v1.taxi`
adds nullable clientId as field 4 with @Since(version = 1), extending the block to
32 bytes. Its Kotlin package differs so both generated readers can coexist in
the test process; schema/template identity remains unchanged on the wire.

SchemaCompatibility tests compare field IDs, names, encodings, nullability,
offsets, and since-version metadata. Existing fields must remain unchanged; new
fields must be appended, optional, and introduced in a newer version. Preserve
baseline schemas when extending the generator. Changing a baseline defeats the
compatibility check and requires explicit review.

Readers validate the minimum block length for the received version and use
actingVersion to return null for absent later fields. Old readers tolerate
appended fixed fields. This does not yet claim compatibility for new groups or
variable data. Encoders initialize optional fields to null when wrapped; callers
must still populate every required field.

## Rich version 0 profile

`examples/trade.taxi` demonstrates numeric enums, a fixed Quote composite, a nullable
client ID, a repeating group of fixed Leg records, and a UTF-8 note. Enums use
explicit uint8 values 0..254; 255 means null only for optional enums. Unknown values
are rejected. Composites concatenate fixed fields without implicit padding.

Fixed fields must precede groups, and groups must precede variable data. Group
dimensions are uint16 blockLength followed by uint16 numInGroup; counts are limited
to 65534. UTF-8 data uses a uint32 byte length; this JVM implementation accepts only
lengths up to Int.MAX_VALUE, further bounded by the supplied message length.
Malformed UTF-8 is rejected when accessed as a String.

Trade has a 33-byte fixed root, including its one-byte Side enum. Tests compare
an 83-byte message with an independent JDK ByteBuffer fixture and test every
truncated prefix. This profile supports multiple root groups/data fields but not
nested variable groups, variable composites, recursive models, or rich schema
evolution. The planner rejects those combinations rather than inventing a layout.

Rich encoders expose `encode(buffer, offset, pojo): Int`, returning total message
length. Decoders expose `wrap(buffer, offset, length)`. Fixed composites and group
entries take a caller-owned reusable decoder target, e.g. `legs(index, legView)`.
`legsCount` exposes the group size. A rich Flyweight is an alias of its Decoder;
there is only one layout implementation. `note()` allocates a String;
`copyNoteTo(target, offset)` copies bytes directly without constructing one.
`toPojo()` deliberately allocates an owned object graph, including lists and
nested records. Do not describe these materialization paths as allocation-free.
