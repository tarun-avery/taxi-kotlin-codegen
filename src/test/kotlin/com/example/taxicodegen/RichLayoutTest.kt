package com.example.taxicodegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RichLayoutTest {
    private val source = Files.readString(Path.of("examples/trade.taxi"))

    private fun compile(text: String) = assertNotNull(TaxiCompilerAdapter().compile(text, "trade.taxi").schema)

    @Test
    fun `rich plan retains explicit enums and computes fixed sizes`() {
        val schema = compile(source)
        assertEquals(mapOf("Buy" to 1, "Sell" to 2), schema.enums.single().values)
        val plan = RichLayoutPlanner().plan(schema)
        assertTrue(plan.diagnostics.isEmpty(), plan.diagnostics.toString())
        assertEquals(33, plan.records.getValue("trading.rich.Trade").blockLength)
        assertEquals(16, plan.records.getValue("trading.rich.Leg").blockLength)
        assertEquals(CodecGenerator().generate(schema), CodecGenerator().generate(schema))
    }

    @Test
    fun `variable nested records and nullable composites are rejected`() {
        val invalid =
            listOf(
                source.replace("quote: Quote", "quote: Quote?"),
                source.replace("@Field(id = 2) quantity: Quantity", "@Field(id = 2) quantity: Note"),
                source.replace("version = 0", "version = 1"),
            )
        invalid.forEach {
            val result = CodecGenerator().generate(compile(it))
            assertTrue(result.sources.isEmpty())
            assertTrue(result.diagnostics.isNotEmpty())
        }
    }
}
