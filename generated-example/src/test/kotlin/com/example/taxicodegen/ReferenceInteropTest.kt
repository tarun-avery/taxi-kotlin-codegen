package com.example.taxicodegen

import org.agrona.concurrent.UnsafeBuffer
import trading.OrderDecoder
import trading.OrderEncoder
import trading.OrderPojo
import trading.rich.LegPojo
import trading.rich.QuotePojo
import trading.rich.Side
import trading.rich.TradeDecoder
import trading.rich.TradeEncoder
import trading.rich.TradePojo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ReferenceInteropTest {
    @Test
    fun `Order interoperates with official SBE codec at nonzero offset`() {
        val ours = UnsafeBuffer(ByteArray(64))
        val oracle = UnsafeBuffer(ByteArray(64))
        val value = OrderPojo(123, 100500, 10)
        OrderEncoder().wrapAndApplyHeader(ours, 7).fromPojo(value)
        reference
            .OrderEncoder()
            .wrapAndApplyHeader(oracle, 7, reference.MessageHeaderEncoder())
            .orderId(123)
            .price(100500)
            .quantity(10)
        assertContentEquals(ours.byteArray(), oracle.byteArray())
        assertEquals(value, OrderDecoder().wrapAndApplyHeader(oracle, 7, 32).toPojo())
        val header = reference.MessageHeaderDecoder().wrap(ours, 7)
        val reader = reference.OrderDecoder().wrap(ours, 15, header.blockLength(), header.version())
        assertEquals(value, OrderPojo(reader.orderId(), reader.price(), reader.quantity()))
    }

    @Test
    fun `rich records interoperate including empty and null representations`() {
        val populated = TradePojo(123, Side.Buy, QuotePojo(100500, 10), null, listOf(LegPojo(1, 2), LegPojo(3, 4)), "µ")
        for (value in listOf(populated, populated.copy(side = null, clientId = 99, legs = emptyList(), note = ""))) {
            val ours = UnsafeBuffer(ByteArray(128))
            val oracle = UnsafeBuffer(ByteArray(128))
            val length = TradeEncoder().encode(ours, 7, value)
            val writer = reference.TradeEncoder().wrapAndApplyHeader(oracle, 7, reference.MessageHeaderEncoder())
            writer
                .id(value.id)
                .side(if (value.side == null) reference.Side.NULL_VAL else reference.Side.Buy)
                .clientId(value.clientId ?: reference.TradeEncoder.clientIdNullValue())
            writer.quote().price(value.quote.price).quantity(value.quote.quantity)
            val group = writer.legsCount(value.legs.size)
            value.legs.forEach { group.next().id(it.id).quantity(it.quantity) }
            writer.note(value.note)
            assertEquals(length, writer.encodedLength() + 8)
            assertContentEquals(ours.byteArray(), oracle.byteArray())
            assertEquals(value, TradeDecoder().wrap(oracle, 7, length).toPojo())
            val header = reference.MessageHeaderDecoder().wrap(ours, 7)
            val reader = reference.TradeDecoder().wrap(ours, 15, header.blockLength(), header.version())
            assertEquals(value.id, reader.id())
            assertEquals(if (value.side == null) reference.Side.NULL_VAL else reference.Side.Buy, reader.side())
            assertEquals(value.clientId ?: Long.MIN_VALUE, reader.clientId())
            assertEquals(value.quote, QuotePojo(reader.quote().price(), reader.quote().quantity()))
            assertEquals(value.legs, reader.legs().map { LegPojo(it.id(), it.quantity()) })
            assertEquals(value.note, reader.note())
        }
    }
}
