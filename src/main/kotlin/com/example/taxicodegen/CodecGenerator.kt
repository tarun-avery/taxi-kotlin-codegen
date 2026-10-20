package com.example.taxicodegen

class CodecGenerator {
    fun generate(schema: DomainSchema): GenerationResult {
        val pojos = PojoGenerator().generate(schema)
        if (pojos.diagnostics.isNotEmpty()) return pojos
        val plan = WireLayoutPlanner().plan(schema)
        if (plan.diagnostics.isNotEmpty()) return GenerationResult(emptyList(), plan.diagnostics)
        return GenerationResult(pojos.sources + plan.messages.map { emit(it) }, emptyList())
    }

    private fun emit(message: WireMessage): GeneratedSource {
        val model = message.model
        val name = model.name.substringAfterLast('.')
        val namespace = model.name.substringBeforeLast('.', "")
        val header = if (namespace.isEmpty()) "" else "package ${namespace.split('.').joinToString(".") { "`$it`" }}"
        val fields = message.fields
        val optionalDefaults =
            fields.filter { it.field.nullable }.joinToString("\n") {
                "        buffer.putLong(offset + ${it.offset}, Long.MIN_VALUE, ByteOrder.LITTLE_ENDIAN)"
            }
        val setters =
            fields.joinToString("\n") { field ->
                """
                fun `${field.field.name}`(value: Long${if (field.field.nullable) "?" else ""}): ${name}Encoder {
                    require(value != Long.MIN_VALUE) { "Reserved SBE null value" }
                    buffer.putLong(offset + ${field.offset}, ${if (field.field.nullable) "value ?: Long.MIN_VALUE" else "value"}, ByteOrder.LITTLE_ENDIAN)
                    return this
                }
                """.trimIndent()
            }
        val getters =
            fields.joinToString("\n") { field ->
                """
                fun `${field.field.name}`(): Long${if (field.field.nullable) "?" else ""} {
                    ${if (field.sinceVersion > 0) "if (version < ${field.sinceVersion}) return null" else ""}
                    val value = buffer.getLong(offset + ${field.offset}, ByteOrder.LITTLE_ENDIAN)
                    ${if (field.field.nullable) "if (value == Long.MIN_VALUE) return null" else "require(value != Long.MIN_VALUE) { \"Required field contains SBE null\" }"}
                    return value
                }
                """.trimIndent()
            }
        val properties =
            fields.joinToString("\n") {
                "    val `${it.field.name}`: Long${if (it.field.nullable) "?" else ""} get() = decoder.`${it.field.name}`()"
            }
        val copy = fields.joinToString(", ") { "`${it.field.name}`()" }
        val encode = fields.joinToString("\n") { "        `${it.field.name}`(value.`${it.field.name}`)" }
        val lengthChecks =
            fields.joinToString("\n") {
                "        require(actingVersion < ${it.sinceVersion} || actingBlockLength >= ${it.offset + 8}) { \"Truncated versioned root block\" }"
            }
        val content =
            """
// Generated from Taxi. Do not edit.
$header

import java.nio.ByteOrder
import org.agrona.DirectBuffer
import org.agrona.MutableDirectBuffer

class ${name}Encoder {
    private lateinit var buffer: MutableDirectBuffer
    private var offset = 0

    fun wrap(buffer: MutableDirectBuffer, offset: Int): ${name}Encoder {
        buffer.boundsCheck(offset, BLOCK_LENGTH)
        this.buffer = buffer
        this.offset = offset
$optionalDefaults
        return this
    }

    fun wrapAndApplyHeader(buffer: MutableDirectBuffer, offset: Int): ${name}Encoder {
        buffer.boundsCheck(offset, HEADER_LENGTH + BLOCK_LENGTH)
        buffer.putShort(offset, BLOCK_LENGTH.toShort(), ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(offset + 2, TEMPLATE_ID.toShort(), ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(offset + 4, SCHEMA_ID.toShort(), ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(offset + 6, SCHEMA_VERSION.toShort(), ByteOrder.LITTLE_ENDIAN)
        return wrap(buffer, offset + HEADER_LENGTH)
    }

$setters

    fun fromPojo(value: ${name}Pojo): ${name}Encoder {
$encode
        return this
    }

    companion object {
        const val HEADER_LENGTH = 8
        const val BLOCK_LENGTH = ${message.blockLength}
        const val TEMPLATE_ID = ${message.templateId}
        const val SCHEMA_ID = ${message.schemaId}
        const val SCHEMA_VERSION = ${message.version}
    }
}

/** Borrows the buffer; callers own its lifetime and must serialize access. */
class ${name}Decoder {
    private var version = 0
    private lateinit var buffer: DirectBuffer
    private var offset = 0

    fun wrap(buffer: DirectBuffer, offset: Int, actingBlockLength: Int, actingVersion: Int): ${name}Decoder {
        require(actingVersion in 0..65534) { "Invalid schema version" }
        require(actingBlockLength in 0..65534) { "Invalid root block length" }
$lengthChecks
        buffer.boundsCheck(offset, actingBlockLength)
        this.version = actingVersion
        this.buffer = buffer
        this.offset = offset
        return this
    }

    fun wrapAndApplyHeader(buffer: DirectBuffer, offset: Int, length: Int): ${name}Decoder {
        require(length >= 8) { "Truncated SBE header" }
        buffer.boundsCheck(offset, length)
        val blockLength = buffer.getShort(offset, ByteOrder.LITTLE_ENDIAN).toInt() and 65535
        val templateId = buffer.getShort(offset + 2, ByteOrder.LITTLE_ENDIAN).toInt() and 65535
        val schemaId = buffer.getShort(offset + 4, ByteOrder.LITTLE_ENDIAN).toInt() and 65535
        val version = buffer.getShort(offset + 6, ByteOrder.LITTLE_ENDIAN).toInt() and 65535
        require(templateId == ${message.templateId} && schemaId == ${message.schemaId}) { "Unexpected SBE message identity" }
        require(blockLength <= length - 8) { "Truncated SBE body" }
        return wrap(buffer, offset + 8, blockLength, version)
    }

$getters

    fun toPojo(): ${name}Pojo = ${name}Pojo($copy)
}

class ${name}Flyweight {
    private val decoder = ${name}Decoder()

    fun wrap(buffer: DirectBuffer, offset: Int, length: Int): ${name}Flyweight {
        decoder.wrapAndApplyHeader(buffer, offset, length)
        return this
    }

$properties

    fun toPojo(): ${name}Pojo = decoder.toPojo()
}
            """.trimIndent() + "\n"
        return GeneratedSource(
            model.name.replace('.', '/').substringBeforeLast('/', "").let {
                if (it.isEmpty()) "${name}Codec.kt" else "$it/${name}Codec.kt"
            },
            content,
        )
    }
}
