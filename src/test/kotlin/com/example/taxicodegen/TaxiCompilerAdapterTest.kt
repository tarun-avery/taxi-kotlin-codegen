package com.example.taxicodegen

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaxiCompilerAdapterTest {
    private val compiler = TaxiCompilerAdapter()

    @Test
    fun `example resolves semantic types and typed annotations in declaration order`() {
        val result = compiler.compile(Files.readString(Path.of("examples/order.taxi")), "examples/order.taxi")
        val schema = assertNotNull(result.schema, result.diagnostics.toString())
        val order = schema.messages.single()
        assertEquals("trading.Order", order.name)
        assertEquals(listOf("orderId", "price", "quantity"), order.fields.map { it.name })
        assertEquals(listOf("trading.OrderId", "trading.Price", "trading.Quantity"), order.fields.map { it.semanticType })
        assertTrue(order.fields.all { it.primitive == DomainPrimitive.LONG && !it.nullable })
        assertEquals("trading.Message", order.annotations.single().name)
        assertEquals(
            "1",
            order.annotations
                .single()
                .parameters["id"]
                .toString(),
        )
        assertEquals(
            "2",
            order.fields[1]
                .annotations
                .single()
                .parameters["id"]
                .toString(),
        )
        assertEquals(
            21,
            order.fields
                .first()
                .position.line,
        )
        assertTrue(
            order.fields
                .first()
                .position.source
                .endsWith("examples/order.taxi"),
        )
    }

    @Test
    fun `inherited scalar aliases and nullable fields are preserved`() {
        val result = compiler.compile("type Id inherits Long\ntype OrderId inherits Id\nmodel Order { id: OrderId? }", "nullable.taxi")
        val field =
            assertNotNull(result.schema, result.diagnostics.toString())
                .messages
                .single()
                .fields
                .single()
        assertEquals("OrderId", field.semanticType)
        assertTrue(field.nullable)
        assertEquals(DomainPrimitive.LONG, field.primitive)
    }

    @Test
    fun `compiler diagnostics retain file line and column`() {
        val result = compiler.compile("model Order {\n    id: MissingType\n}", "broken.taxi")
        assertNull(result.schema)
        val error = result.diagnostics.first { it.severity == "ERROR" }
        assertTrue(error.position.source.endsWith("broken.taxi"))
        assertEquals(2, error.position.line)
        assertTrue(error.position.column > 0)
        assertTrue(error.message.contains("MissingType"))
    }

    @Test
    fun `syntax errors produce diagnostics without a partial schema`() {
        val result = compiler.compile("model Order { id:", "syntax.taxi")
        assertNull(result.schema)
        assertTrue(result.diagnostics.any { it.severity == "ERROR" })
    }

    @Test
    fun `unsupported fields fail rather than disappear`() {
        for (fieldType in listOf("Decimal", "Int", "Long[]")) {
            val result = compiler.compile("model Order { id: Long\n unsupported: $fieldType }", "unsupported.taxi")
            assertNull(result.schema, fieldType)
            assertTrue(result.diagnostics.any { it.message.contains("unsupported") && it.position.line == 2 })
        }
    }

    @Test
    fun `empty source and enum declarations are rejected`() {
        assertNull(compiler.compile("", "empty.taxi").schema)
        val result = compiler.compile("enum Side { Buy, Sell }\nmodel Order { id: Long }", "enum.taxi")
        assertNull(result.schema)
        assertTrue(result.diagnostics.any { it.message.contains("Unsupported type Side") })
    }

    @Test
    fun `model inheritance is rejected`() {
        val result = compiler.compile("model Base { id: Long }\nmodel Order inherits Base { quantity: Long }", "inheritance.taxi")
        assertNull(result.schema)
        assertTrue(result.diagnostics.any { it.message.contains("inheritance") })
    }

    @Test
    fun `function declarations are rejected with their source position`() {
        val result =
            compiler.compile(
                "declare function id():Long\nmodel Order { id: Long }",
                "function.taxi",
            )
        assertNull(result.schema)
        assertTrue(result.diagnostics.any { it.message.contains("Function declarations") && it.position.line == 1 })
    }

    @Test
    fun `invalid annotation arguments are compiler errors`() {
        val result =
            compiler.compile(
                """annotation Message { id: Int }
@Message(id = "bad")
model Order { id: Long }""",
                "annotation.taxi",
            )
        assertNull(result.schema)
        assertTrue(result.diagnostics.any { it.severity == "ERROR" })
    }

    @Test
    fun `repeated compilation has stable ordering and output`() {
        val source = "model Z { second: Long first: Long }\nmodel A { id: Long }"
        val first = compiler.compile(source, "stable.taxi")
        assertEquals(first, compiler.compile(source, "stable.taxi"))
        assertEquals(listOf("A", "Z"), assertNotNull(first.schema).messages.map { it.name })
        assertEquals(
            listOf("second", "first"),
            first.schema.messages
                .last()
                .fields
                .map { it.name },
        )
    }

    @Test
    fun `cli prints resolved model and reports usage and missing files`() {
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val out = PrintStream(output)
        val err = PrintStream(errors)
        assertEquals(0, runCli(arrayOf("examples/order.taxi"), out, err), errors.toString())
        assertTrue(output.toString().contains("orderId: trading.OrderId -> LONG"))
        assertEquals(2, runCli(emptyArray(), out, err))
        assertEquals(2, runCli(arrayOf("does-not-exist.taxi"), out, err))
    }
}
