package com.example.taxicodegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FullRangeLayoutTest {
    private val source = Files.readString(Path.of("examples/full-range.taxi"))

    private fun schema(source: String) = assertNotNull(TaxiCompilerAdapter().compile(source, "full-range.taxi").schema)

    @Test
    fun `full range is explicit deterministic and part of compatibility`() {
        val model = schema(source)
        val layout = WireLayoutPlanner().plan(model)
        assertTrue(layout.diagnostics.isEmpty())
        assertEquals(
            listOf(0, 9),
            layout.messages
                .single()
                .fields
                .map { it.offset },
        )
        assertEquals(17, layout.messages.single().blockLength)
        assertEquals(CodecGenerator().generate(model), CodecGenerator().generate(model))
        val ordinary = schema(source.replace("@FullRange\n", ""))
        assertTrue(SchemaCompatibility().check(model, ordinary).isNotEmpty())
        assertTrue(SchemaCompatibility().check(ordinary, model).isNotEmpty())
        assertTrue(
            CodecGenerator().generate(schema(source.replace("value: CounterValue", "value: CounterValue?"))).diagnostics.isNotEmpty(),
        )
    }
}
