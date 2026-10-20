package com.example.taxicodegen

import org.agrona.concurrent.UnsafeBuffer
import trading.OrderDecoder
import trading.OrderEncoder
import trading.OrderPojo
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import trading.v1.OrderDecoder as NewDecoder
import trading.v1.OrderEncoder as NewEncoder
import trading.v1.OrderPojo as NewPojo

class EvolutionTest {
    @Test
    fun `new decoder reads old payload and reports absent field`() {
        val buffer = UnsafeBuffer(ByteArray(32))
        OrderEncoder().wrapAndApplyHeader(buffer, 0).fromPojo(OrderPojo(1, 2, 3))
        val decoded = NewDecoder().wrapAndApplyHeader(buffer, 0, 32).toPojo()
        assertEquals(NewPojo(1, 2, 3, null), decoded)
    }

    @Test
    fun `old decoder reads new payload ignoring appended field`() {
        val buffer = UnsafeBuffer(ByteArray(40))
        NewEncoder().wrapAndApplyHeader(buffer, 0).fromPojo(NewPojo(1, 2, 3, 123))
        assertEquals(OrderPojo(1, 2, 3), OrderDecoder().wrapAndApplyHeader(buffer, 0, 40).toPojo())
        assertEquals(123L, NewDecoder().wrapAndApplyHeader(buffer, 0, 40).clientId())
        NewEncoder().wrap(buffer, 8).clientId(null)
        assertEquals(Long.MIN_VALUE, buffer.getLong(32, ByteOrder.LITTLE_ENDIAN))
        assertNull(NewDecoder().wrapAndApplyHeader(buffer, 0, 40).clientId())
    }

    @Test
    fun `version cannot excuse a short current block`() {
        val buffer = UnsafeBuffer(ByteArray(40))
        NewEncoder().wrapAndApplyHeader(buffer, 0).fromPojo(NewPojo(1, 2, 3, null))
        buffer.putShort(0, 24, ByteOrder.LITTLE_ENDIAN)
        assertFailsWith<IllegalArgumentException> { NewDecoder().wrapAndApplyHeader(buffer, 0, 40) }
    }
}
