package com.example.taxicodegen

import trading.OrderPojo
import kotlin.test.Test
import kotlin.test.assertEquals

class AeronDemoTest {
    @Test
    fun `order traverses real Aeron IPC and returns an owned copy`() {
        val order = OrderPojo(987, 1234500, 25)
        assertEquals(order, exchangeOrder(order))
    }
}
