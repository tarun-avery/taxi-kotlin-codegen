package com.example.taxicodegen

import org.agrona.concurrent.UnsafeBuffer
import trading.OrderDecoder
import trading.OrderEncoder
import trading.OrderFlyweight
import trading.OrderPojo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderCodecTest {
    // Independently derived LE bytes: uint16 header 24,1,1,0; int64 body 123,100500,10.
    private val fixture =
        "18000100010000007b0000000000000094880100000000000a00000000000000"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

    @Test
    fun `encoder matches fixed SBE fixture at a nonzero offset`() {
        val bytes = ByteArray(48) { 0x55 }
        val buffer = UnsafeBuffer(bytes)
        OrderEncoder().wrapAndApplyHeader(buffer, 5).fromPojo(OrderPojo(123, 100500, 10))
        assertContentEquals(fixture, bytes.copyOfRange(5, 37))
        assertEquals(0x55.toByte(), bytes[4])
        assertEquals(0x55.toByte(), bytes[37])
        // Independent JDK byte reader, not our decoder.
        assertEquals(100500L, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong(21))
    }

    @Test
    fun `decoder and flyweight read independent fixture without copying`() {
        val buffer = UnsafeBuffer(fixture.copyOf())
        val decoder = OrderDecoder().wrapAndApplyHeader(buffer, 0, fixture.size)
        val view = OrderFlyweight().wrap(buffer, 0, fixture.size)
        val owned = decoder.toPojo()
        assertEquals(OrderPojo(123, 100500, 10), owned)
        assertEquals(owned, view.toPojo())
        buffer.putLong(8, 456, ByteOrder.LITTLE_ENDIAN)
        assertEquals(456L, view.orderId)
        assertEquals(123L, owned.orderId)
    }

    @Test
    fun `truncation identity and null sentinel are rejected`() {
        val buffer = UnsafeBuffer(fixture.copyOf())
        for (length in 0 until fixture.size) {
            assertFailsWith<IllegalArgumentException> { OrderDecoder().wrapAndApplyHeader(buffer, 0, length) }
        }
        buffer.putShort(2, 99, ByteOrder.LITTLE_ENDIAN)
        assertFailsWith<IllegalArgumentException> { OrderDecoder().wrapAndApplyHeader(buffer, 0, 32) }
        assertFailsWith<IndexOutOfBoundsException> { OrderEncoder().wrapAndApplyHeader(buffer, 1) }
        assertFailsWith<IllegalArgumentException> { OrderEncoder().wrap(buffer, 0).orderId(Long.MIN_VALUE) }
        buffer.putLong(8, Long.MIN_VALUE, ByteOrder.LITTLE_ENDIAN)
        assertFailsWith<IllegalArgumentException> { OrderDecoder().wrap(buffer, 8, 24, 0).orderId() }
    }
}
