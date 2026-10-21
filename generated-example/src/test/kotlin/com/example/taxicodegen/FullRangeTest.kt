package com.example.taxicodegen

import fullrange.CounterDecoder
import fullrange.CounterEncoder
import fullrange.CounterPojo
import org.agrona.concurrent.UnsafeBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FullRangeTest {
    @Test
    fun `all Long boundary values interoperate with independent SBE composite codecs`() {
        for (value in listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE)) {
            val ours = UnsafeBuffer(ByteArray(40))
            val oracle = UnsafeBuffer(ByteArray(40))
            CounterEncoder().wrapAndApplyHeader(ours, 3).fromPojo(CounterPojo(value, 42))
            val writer = reference.CounterEncoder().wrapAndApplyHeader(oracle, 3, reference.MessageHeaderEncoder())
            writer
                .value()
                .tag(
                    if (value ==
                        Long.MIN_VALUE
                    ) {
                        1.toShort()
                    } else {
                        0.toShort()
                    },
                ).rawValue(if (value == Long.MIN_VALUE) 0 else value)
            writer.sequence(42)
            assertContentEquals(oracle.byteArray(), ours.byteArray())
            assertEquals(CounterPojo(value, 42), CounterDecoder().wrapAndApplyHeader(oracle, 3, 25).toPojo())
            val header = reference.MessageHeaderDecoder().wrap(ours, 3)
            val reader = reference.CounterDecoder().wrap(ours, 11, header.blockLength(), header.version())
            assertEquals(value, if (reader.value().tag().toInt() == 1) Long.MIN_VALUE else reader.value().rawValue())
            assertEquals(42, reader.sequence())
        }
    }

    @Test
    fun `minimum value has an independently specified canonical byte fixture`() {
        val buffer = UnsafeBuffer(ByteArray(25))
        CounterEncoder().wrapAndApplyHeader(buffer, 0).fromPojo(CounterPojo(Long.MIN_VALUE, 42))
        val fixture = "11000300010000000100000000000000002a00000000000000".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertContentEquals(fixture, buffer.byteArray())
        for (length in 0 until 25) assertFailsWith<IllegalArgumentException> { CounterDecoder().wrapAndApplyHeader(buffer, 0, length) }
        for ((tag, raw) in listOf(2 to 0L, 1 to 3L, 0 to Long.MIN_VALUE)) {
            buffer.putByte(8, tag.toByte())
            buffer.putLong(9, raw, ByteOrder.LITTLE_ENDIAN)
            assertFailsWith<IllegalArgumentException> { CounterDecoder().wrapAndApplyHeader(buffer, 0, 25).value() }
        }
    }
}
