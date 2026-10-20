package com.example.taxicodegen

/** Rich records use explicit group indexes and reusable target views; strings allocate only on access. */
class RichCodecGenerator {
    fun generate(schema: DomainSchema): GenerationResult {
        val pojos = PojoGenerator().generate(schema)
        if (pojos.diagnostics.isNotEmpty()) return pojos
        val plan = RichLayoutPlanner().plan(schema)
        if (plan.diagnostics.isNotEmpty()) return GenerationResult(emptyList(), plan.diagnostics)
        return GenerationResult(pojos.sources + plan.records.values.map { emit(it, plan) }, emptyList())
    }

    private fun emit(
        record: RichRecord,
        plan: RichPlan,
    ): GeneratedSource {
        val model = record.model
        val name = model.name.substringAfterLast('.')
        val namespace = model.name.substringBeforeLast('.', "")
        val root = plan.roots.find { it.model.name == model.name }

        fun qualified(
            type: String,
            suffix: String,
        ) = (type + suffix).split('.').joinToString(".") { "`$it`" }
        val source =
            buildString {
                appendLine("// Generated from Taxi. Do not edit.")
                if (namespace.isNotEmpty()) appendLine("package ${namespace.split('.').joinToString(".") { "`$it`" }}")
                appendLine("import java.nio.ByteOrder.LITTLE_ENDIAN")
                appendLine("import java.nio.ByteBuffer")
                appendLine("import org.agrona.DirectBuffer")
                appendLine("import org.agrona.MutableDirectBuffer")
                appendLine("class ${name}Decoder {")
                appendLine("    private lateinit var buffer: DirectBuffer")
                appendLine("    private var offset = 0")
                record.fields.filter { it.size == 0 }.forEach {
                    appendLine("    private var ${it.field.name}Offset = 0")
                    appendLine("    var ${it.field.name}Count = 0; private set")
                    if (it.field.primitive == DomainPrimitive.GROUP) appendLine("    private var ${it.field.name}Block = 0")
                }
                if (root != null) {
                    appendLine("    fun wrap(buffer: DirectBuffer, offset: Int, length: Int): ${name}Decoder {")
                    appendLine("        require(length >= 8); buffer.boundsCheck(offset, length)")
                    appendLine("        fun u16(at: Int) = buffer.getShort(at, LITTLE_ENDIAN).toInt() and 65535")
                    appendLine("        require(u16(offset + 2) == ${root.templateId} && u16(offset + 4) == ${root.schemaId})")
                    appendLine("        require(u16(offset + 6) == 0) { \"Rich codecs currently require version 0\" }")
                    appendLine("        val block = u16(offset); require(block >= ${record.blockLength} && block <= length - 8)")
                    appendLine("        val end = offset + length; var cursor = offset + 8 + block")
                    record.fields.filter { it.size == 0 }.forEach { item ->
                        val field = item.field
                        val n = field.name
                        if (field.primitive == DomainPrimitive.GROUP) {
                            val entry = plan.records.getValue(field.semanticType)
                            appendLine("        require(end - cursor >= 4) { \"Truncated group dimensions\" }")
                            appendLine("        val ${n}Block = u16(cursor); val ${n}Count = u16(cursor + 2); cursor += 4")
                            appendLine("        require(${n}Block in ${entry.blockLength}..65534 && ${n}Count <= 65534)")
                            appendLine("        require(${n}Count.toLong() * ${n}Block <= end - cursor) { \"Truncated group\" }")
                            appendLine("        this.${n}Offset = cursor; this.${n}Block = ${n}Block; this.${n}Count = ${n}Count")
                            appendLine("        cursor += ${n}Count * ${n}Block")
                        } else {
                            appendLine("        require(end - cursor >= 4) { \"Truncated data length\" }")
                            appendLine("        val ${n}Length = buffer.getInt(cursor, LITTLE_ENDIAN); cursor += 4")
                            appendLine("        require(${n}Length >= 0 && ${n}Length <= end - cursor) { \"Truncated variable data\" }")
                            appendLine("        this.${n}Offset = cursor; this.${n}Count = ${n}Length; cursor += ${n}Length")
                        }
                    }
                    appendLine("        this.buffer = buffer; this.offset = offset + 8; return this")
                } else {
                    appendLine("    fun wrap(buffer: DirectBuffer, offset: Int): ${name}Decoder {")
                    appendLine(
                        "        buffer.boundsCheck(offset, ${record.blockLength}); " +
                            "this.buffer = buffer; this.offset = offset; return this",
                    )
                }
                appendLine("    }")
                record.fields.forEach { item ->
                    val f = item.field
                    val n = f.name
                    val type = f.kotlinType() + if (f.nullable) "?" else ""
                    when (f.primitive) {
                        DomainPrimitive.LONG -> {
                            appendLine("    fun `$n`(): $type {")
                            appendLine("        val value = buffer.getLong(offset + ${item.offset}, LITTLE_ENDIAN)")
                            appendLine(
                                if (f.nullable) {
                                    "        if (value == Long.MIN_VALUE) return null"
                                } else {
                                    "        require(value != Long.MIN_VALUE)"
                                },
                            )
                            appendLine("        return value\n    }")
                        }

                        DomainPrimitive.ENUM -> {
                            appendLine("    fun `$n`(): $type {")
                            appendLine("        val value = buffer.getByte(offset + ${item.offset}).toInt() and 255")
                            if (f.nullable) appendLine("        if (value == 255) return null")
                            appendLine("        return ${qualified(f.semanticType, "")}.entries.firstOrNull { it.wireValue == value }")
                            appendLine("            ?: throw IllegalArgumentException(\"Unknown enum value: \" + value)\n    }")
                        }

                        DomainPrimitive.COMPOSITE -> {
                            val view = qualified(f.semanticType, "Decoder")
                            appendLine("    fun `$n`(target: $view): $view = target.wrap(buffer, offset + ${item.offset})")
                        }

                        DomainPrimitive.GROUP -> {
                            val view = qualified(f.semanticType, "Decoder")
                            appendLine("    fun `$n`(index: Int, target: $view): $view {")
                            appendLine(
                                "        require(index in 0 until ${n}Count); " +
                                    "return target.wrap(buffer, ${n}Offset + index * ${n}Block)\n    }",
                            )
                        }

                        DomainPrimitive.STRING -> {
                            appendLine("    fun `$n`(): String {")
                            appendLine("        val bytes = ByteArray(${n}Count); buffer.getBytes(${n}Offset, bytes)")
                            appendLine("        return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()\n    }")
                            appendLine(
                                "    fun copy${n.replaceFirstChar {
                                    it.uppercaseChar()
                                }}To(target: MutableDirectBuffer, targetOffset: Int) {",
                            )
                            appendLine("        target.putBytes(targetOffset, buffer, ${n}Offset, ${n}Count)\n    }")
                        }
                    }
                }
                val values =
                    record.fields.joinToString(", ") { item ->
                        val f = item.field
                        when (f.primitive) {
                            DomainPrimitive.COMPOSITE -> "`${f.name}`(${qualified(f.semanticType, "Decoder")}()).toPojo()"

                            DomainPrimitive.GROUP -> "List(${f.name}Count) { `${f.name}`(it, ${qualified(
                                f.semanticType,
                                "Decoder",
                            )}()).toPojo() }"

                            else -> "`${f.name}`()"
                        }
                    }
                appendLine("    fun toPojo(): ${name}Pojo = ${name}Pojo($values)")
                appendLine("}")
                appendLine("class ${name}Encoder {")
                if (root != null) {
                    appendLine("    fun encode(buffer: MutableDirectBuffer, offset: Int, value: ${name}Pojo): Int {")
                    record.fields.filter { it.field.primitive == DomainPrimitive.STRING }.forEach {
                        appendLine("        val ${it.field.name}Bytes = value.`${it.field.name}`.toByteArray(Charsets.UTF_8)")
                    }
                    appendLine("        var length = ${record.blockLength + 8}L")
                    record.fields.filter { it.size == 0 }.forEach {
                        val f = it.field
                        if (f.primitive == DomainPrimitive.GROUP) {
                            appendLine("        require(value.`${f.name}`.size <= 65534)")
                            appendLine(
                                "        length += 4L + value.`${f.name}`.size.toLong() * ${plan.records.getValue(
                                    f.semanticType,
                                ).blockLength}",
                            )
                        } else {
                            appendLine("        length += 4L + ${f.name}Bytes.size")
                        }
                    }
                    appendLine("        require(length <= Int.MAX_VALUE); buffer.boundsCheck(offset, length.toInt())")
                    appendLine("        buffer.putShort(offset, ${record.blockLength}.toShort(), LITTLE_ENDIAN)")
                    appendLine("        buffer.putShort(offset + 2, ${root.templateId}.toShort(), LITTLE_ENDIAN)")
                    appendLine("        buffer.putShort(offset + 4, ${root.schemaId}.toShort(), LITTLE_ENDIAN)")
                    appendLine("        buffer.putShort(offset + 6, 0.toShort(), LITTLE_ENDIAN)")
                    appendLine("        writeFixed(buffer, offset + 8, value)")
                    appendLine("        var cursor = offset + ${record.blockLength + 8}")
                    record.fields.filter { it.size == 0 }.forEach {
                        val f = it.field
                        if (f.primitive == DomainPrimitive.GROUP) {
                            val block = plan.records.getValue(f.semanticType).blockLength
                            appendLine("        buffer.putShort(cursor, $block.toShort(), LITTLE_ENDIAN)")
                            appendLine("        buffer.putShort(cursor + 2, value.`${f.name}`.size.toShort(), LITTLE_ENDIAN); cursor += 4")
                            appendLine(
                                "        value.`${f.name}`.forEach { ${qualified(
                                    f.semanticType,
                                    "Encoder",
                                )}.writeFixed(buffer, cursor, it); cursor += $block }",
                            )
                        } else {
                            appendLine("        buffer.putInt(cursor, ${f.name}Bytes.size, LITTLE_ENDIAN); cursor += 4")
                            appendLine("        buffer.putBytes(cursor, ${f.name}Bytes); cursor += ${f.name}Bytes.size")
                        }
                    }
                    appendLine("        return length.toInt()\n    }")
                }
                appendLine("    companion object {")
                appendLine("        fun writeFixed(buffer: MutableDirectBuffer, offset: Int, value: ${name}Pojo) {")
                appendLine("            buffer.boundsCheck(offset, ${record.blockLength})")
                record.fields.filter { it.size > 0 }.forEach {
                    val f = it.field
                    val value = "value.`${f.name}`"
                    when (f.primitive) {
                        DomainPrimitive.LONG -> {
                            appendLine("            require($value != Long.MIN_VALUE)")
                            appendLine(
                                "            buffer.putLong(offset + ${it.offset}, " +
                                    "${if (f.nullable) "$value ?: Long.MIN_VALUE" else value}, LITTLE_ENDIAN)",
                            )
                        }

                        DomainPrimitive.ENUM -> {
                            appendLine(
                                "            buffer.putByte(offset + ${it.offset}, " +
                                    "(${if (f.nullable) "$value?.wireValue ?: 255" else "$value.wireValue"}).toByte())",
                            )
                        }

                        DomainPrimitive.COMPOSITE -> {
                            appendLine(
                                "            ${qualified(f.semanticType, "Encoder")}.writeFixed(buffer, offset + ${it.offset}, $value)",
                            )
                        }

                        else -> {
                            error("Unexpected fixed field")
                        }
                    }
                }
                appendLine("        }\n    }\n}")
                // A domain-facing name for the same borrowed view, with no second offset implementation.
                appendLine("typealias ${name}Flyweight = ${name}Decoder")
            }
        return GeneratedSource(model.name.replace('.', '/') + "Codec.kt", source)
    }
}
