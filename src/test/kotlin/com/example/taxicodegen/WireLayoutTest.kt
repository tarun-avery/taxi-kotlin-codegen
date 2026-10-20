package com.example.taxicodegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WireLayoutTest {
    private val source = Files.readString(Path.of("examples/order.taxi"))

    private fun plan(source: String): LayoutResult =
        WireLayoutPlanner().plan(
            assertNotNull(TaxiCompilerAdapter().compile(source, "order.taxi").schema),
        )

    @Test
    fun `layout assigns fixed offsets and preserves identifiers`() {
        val result = plan(source)
        assertTrue(result.diagnostics.isEmpty())
        val order = result.messages.single()
        assertEquals(24, order.blockLength)
        assertEquals(listOf(0, 8, 16), order.fields.map { it.offset })
        assertEquals(listOf(1, 2, 3), order.fields.map { it.id })
    }

    @Test
    fun `invalid metadata never generates codecs`() {
        val invalid =
            listOf(
                source.replace("@Field(id = 2)", "@Field(id = 1)"),
                source.replace("schemaId = 1", "schemaId = 65535"),
                source.replace("@Field(id = 2)", ""),
                source.replace("version = 0", "version = -1"),
            )
        invalid.forEach {
            val result = plan(it)
            assertTrue(result.messages.isEmpty())
            assertTrue(result.diagnostics.isNotEmpty())
        }
    }
}
