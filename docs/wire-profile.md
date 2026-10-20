# SBE wire profile

This project implements a deliberately limited profile of FIX SBE 1.0 with the
November 2020 errata. It does not claim complete SBE feature support.

All integers are little endian. The message header contains four uint16 fields:
root block length, template ID, schema ID, and schema version. The header is eight
bytes and is excluded from the root block length. IDs and versions use 0..65534;
65535 is reserved. Fields use signed int64, with Long.MIN_VALUE reserved as null.
The initial profile permits only required fields, so encoders and decoders reject
that value. Fields follow declaration order; field IDs do not determine offsets.

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
