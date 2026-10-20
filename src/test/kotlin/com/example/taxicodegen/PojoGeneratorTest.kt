package com.example.taxicodegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PojoGeneratorTest {
    private val generator = PojoGenerator()

    @Test
    fun `order generation is deterministic and keeps declaration order`() {
        val schema = assertNotNull(TaxiCompilerAdapter().compile(Files.readString(Path.of("examples/order.taxi")), "order.taxi").schema)
        val result = generator.generate(schema)
        assertTrue(result.diagnostics.isEmpty())
        assertEquals(result, generator.generate(schema))
        val source = result.sources.single()
        assertEquals("trading/OrderPojo.kt", source.relativePath)
        assertEquals(
            """
            // Generated from Taxi. Do not edit.
            package trading

            data class OrderPojo(
                val orderId: Long,
                val price: Long,
                val quantity: Long,
            )
            """.trimIndent() + "\n",
            source.content,
        )
    }

    @Test
    fun `nullable fields and Kotlin keywords are rendered`() {
        val schema = assertNotNull(TaxiCompilerAdapter().compile("model Order { `when`: Long? }", "keywords.taxi").schema)
        val content =
            generator
                .generate(schema)
                .sources
                .single()
                .content
        assertTrue(content.contains("val `when`: Long?"))
        assertTrue(!content.contains("package "))
    }

    @Test
    fun `empty models and unsafe names fail without partial output`() {
        val position = SourcePosition("bad.taxi", 1, 1)
        val empty = DomainMessage("Empty", emptyList(), emptyList(), position)
        assertTrue(generator.generate(DomainSchema(listOf(empty))).diagnostics.isNotEmpty())
        val unsafe = empty.copy(name = "../Bad")
        val result = generator.generate(DomainSchema(listOf(unsafe)))
        assertTrue(result.sources.isEmpty())
        assertTrue(
            result.diagnostics
                .single()
                .message
                .contains("ASCII identifiers"),
        )
    }
}
