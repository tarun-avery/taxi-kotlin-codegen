package com.example.taxicodegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchemaCompatibilityTest {
    private val oldSource = Files.readString(Path.of("examples/order.taxi"))
    private val newSource = Files.readString(Path.of("examples/order-v1.taxi"))

    private fun schema(source: String) = assertNotNull(TaxiCompilerAdapter().compile(source, "schema.taxi").schema)

    @Test
    fun `preserved v0 baseline is compatible with v1`() {
        assertTrue(SchemaCompatibility().check(schema(oldSource), schema(newSource)).isEmpty())
    }

    @Test
    fun `changed identity field order IDs nullability and version fail`() {
        val changes =
            listOf(
                newSource.replace("schemaId = 1", "schemaId = 2"),
                newSource.replace("@Field(id = 2)", "@Field(id = 22)"),
                newSource.replace("price: Price", "price: Price?"),
                newSource.replace("version = 1", "version = 0"),
                newSource.replace("orderId: OrderId", "renamed: OrderId"),
            )
        changes.forEach { assertTrue(SchemaCompatibility().check(schema(oldSource), schema(it)).isNotEmpty()) }
    }
}
