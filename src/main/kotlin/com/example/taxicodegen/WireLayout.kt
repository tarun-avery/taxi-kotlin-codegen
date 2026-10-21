package com.example.taxicodegen

data class WireField(
    val field: DomainField,
    val id: Int,
    val offset: Int,
    val sinceVersion: Int = 0,
    val fullRange: Boolean = false,
) {
    val encodedLength: Int get() = if (fullRange) 9 else 8
}

data class WireMessage(
    val model: DomainMessage,
    val templateId: Int,
    val schemaId: Int,
    val version: Int,
    val fields: List<WireField>,
) {
    val blockLength: Int get() = fields.sumOf { it.encodedLength }
}

data class LayoutResult(
    val messages: List<WireMessage>,
    val diagnostics: List<Diagnostic>,
)

class WireLayoutPlanner {
    fun plan(schema: DomainSchema): LayoutResult {
        val errors = mutableListOf<Diagnostic>()

        fun number(
            annotation: DomainAnnotation?,
            key: String,
            position: SourcePosition,
        ): Int {
            val value = annotation?.parameters?.get(key)
            val number = (value as? Number)?.toLong()
            if (number == null || number !in 0..65534 || value.toString() != number.toString()) {
                errors += Diagnostic(position, "SBE $key must be an integer in 0..65534.")
                return 0
            }
            return number.toInt()
        }

        fun annotation(
            annotations: List<DomainAnnotation>,
            name: String,
            position: SourcePosition,
        ): DomainAnnotation? {
            val found = annotations.filter { it.name.substringAfterLast('.') == name }
            if (found.size != 1) errors += Diagnostic(position, "Exactly one @$name annotation is required.")
            return found.singleOrNull()
        }
        val messages =
            schema.messages.map { model ->
                val metadata = annotation(model.annotations, "Message", model.position)
                val version = number(metadata, "version", model.position)
                var offset = 0
                val fields =
                    model.fields.map { field ->
                        val since = field.annotations.filter { it.name.substringAfterLast('.') == "Since" }
                        if (since.size > 1) errors += Diagnostic(field.position, "Duplicate @Since annotation.")
                        val sinceVersion = if (since.isEmpty()) 0 else number(since.first(), "version", field.position)
                        if (sinceVersion > version) errors += Diagnostic(field.position, "Field version exceeds schema version.")
                        if (sinceVersion > 0 && !field.nullable) errors += Diagnostic(field.position, "Added fields must be optional.")
                        val fieldMetadata = annotation(field.annotations, "Field", field.position)
                        val fullRange = field.annotations.filter { it.name.substringAfterLast('.') == "FullRange" }
                        if (fullRange.size > 1 || (fullRange.isNotEmpty() && field.nullable)) {
                            errors += Diagnostic(field.position, "@FullRange requires a non-null Long field and cannot be repeated.")
                        }
                        WireField(field, number(fieldMetadata, "id", field.position), offset, sinceVersion, fullRange.isNotEmpty())
                            .also { offset += it.encodedLength }
                    }
                if (fields.zipWithNext().any { (a, b) -> a.sinceVersion > b.sinceVersion }) {
                    errors += Diagnostic(model.position, "Versioned fields must be appended in version order.")
                }
                if (fields.map { it.id }.distinct().size != fields.size) errors += Diagnostic(model.position, "Duplicate SBE field IDs.")
                if (offset > 65534) errors += Diagnostic(model.position, "Root block exceeds uint16 length.")
                WireMessage(
                    model,
                    number(metadata, "id", model.position),
                    number(metadata, "schemaId", model.position),
                    version,
                    fields,
                )
            }
        if (messages.map { it.schemaId to it.templateId }.distinct().size != messages.size) {
            errors += Diagnostic(schema.messages.first().position, "Duplicate schema/template IDs.")
        }
        return LayoutResult(if (errors.isEmpty()) messages else emptyList(), errors)
    }
}
