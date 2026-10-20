package com.example.taxicodegen

import org.agrona.concurrent.UnsafeBuffer
import trading.rich.LegDecoder
import trading.rich.LegPojo
import trading.rich.QuoteDecoder
import trading.rich.QuotePojo
import trading.rich.Side
import trading.rich.TradeDecoder
import trading.rich.TradeEncoder
import trading.rich.TradePojo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RichCodecTest {
    private val value = TradePojo(123, Side.Buy, QuotePojo(100500, 10), null, listOf(LegPojo(1, 2), LegPojo(3, 4)), "µ")

    // Independent, explicit SBE layout: root 33 bytes, group dimension 4, two 16-byte entries, uint32 data length + UTF-8.
    private fun fixture(): ByteArray {
        val bytes = ByteBuffer.allocate(83).order(ByteOrder.LITTLE_ENDIAN)
        bytes
            .putShort(33)
            .putShort(2)
            .putShort(1)
            .putShort(0)
        bytes
            .putLong(123)
            .put(1)
            .putLong(100500)
            .putLong(10)
            .putLong(Long.MIN_VALUE)
        bytes
            .putShort(16)
            .putShort(2)
            .putLong(1)
            .putLong(2)
            .putLong(3)
            .putLong(4)
        bytes.putInt(2).put(0xc2.toByte()).put(0xb5.toByte())
        return bytes.array()
    }

    @Test
    fun `rich codec matches independent SBE fixture and views reuse buffers`() {
        val bytes = ByteArray(100)
        val buffer = UnsafeBuffer(bytes)
        assertEquals(83, TradeEncoder().encode(buffer, 7, value))
        assertContentEquals(fixture(), bytes.copyOfRange(7, 90))
        val reader = TradeDecoder().wrap(UnsafeBuffer(fixture()), 0, 83)
        assertEquals(value, reader.toPojo())
        assertEquals(2, reader.legsCount)
        val leg = LegDecoder()
        assertEquals(1L, reader.legs(0, leg).id())
        assertEquals(3L, reader.legs(1, leg).id())
        assertEquals(100500L, reader.quote(QuoteDecoder()).price())
        val copy = UnsafeBuffer(ByteArray(2))
        reader.copyNoteTo(copy, 0)
        assertContentEquals("µ".toByteArray(), copy.byteArray())
    }

    @Test
    fun `empty groups and empty variable data round trip`() {
        val empty = value.copy(legs = emptyList(), note = "", clientId = 99, side = null)
        val buffer = UnsafeBuffer(ByteArray(100))
        val length = TradeEncoder().encode(buffer, 0, empty)
        assertEquals(49, length)
        assertEquals(empty, TradeDecoder().wrap(buffer, 0, length).toPojo())
        assertEquals(255, buffer.getByte(16).toInt() and 255)
        assertNull(TradeDecoder().wrap(buffer, 0, length).side())
    }

    @Test
    fun `truncated lengths invalid counts and unknown enum values fail`() {
        val buffer = UnsafeBuffer(fixture())
        for (length in 0 until 83) {
            assertFailsWith<IllegalArgumentException> { TradeDecoder().wrap(buffer, 0, length) }
        }
        buffer.putShort(43, (-1).toShort(), ByteOrder.LITTLE_ENDIAN)
        assertFailsWith<IllegalArgumentException> { TradeDecoder().wrap(buffer, 0, 83) }
        val unknown = UnsafeBuffer(fixture())
        unknown.putByte(16, 99)
        assertFailsWith<IllegalArgumentException> { TradeDecoder().wrap(unknown, 0, 83).side() }
        unknown.putInt(77, Int.MAX_VALUE, ByteOrder.LITTLE_ENDIAN)
        assertFailsWith<IllegalArgumentException> { TradeDecoder().wrap(unknown, 0, 83) }
        assertFailsWith<IndexOutOfBoundsException> { TradeEncoder().encode(UnsafeBuffer(ByteArray(82)), 0, value) }
    }
}
