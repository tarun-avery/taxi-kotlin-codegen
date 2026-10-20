package com.example.taxicodegen

import trading.OrderPojo
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedPojoTest {
    @Test
    fun `generated order has value semantics`() {
        val order = OrderPojo(123, 100500, 10)
        assertEquals(123L, order.orderId)
        assertEquals(100500L, order.price)
        assertEquals(10L, order.quantity)
        assertEquals(OrderPojo(123, 100500, 10), order)
        assertEquals(11L, order.copy(quantity = 11).quantity)
        val (id, price, quantity) = order
        assertEquals(listOf(123L, 100500L, 10L), listOf(id, price, quantity))
    }
}
