package com.example.taxicodegen

data class WireField(
    val field: DomainField,
    val id: Int,
    val offset: Int,
)

data class WireMessage(
    val model: DomainMessage,
    val templateId: Int,
    val schemaId: Int,
    val version: Int,
    val fields: List<WireField>,
) {
    val blockLength: Int get() = fields.size * 8
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
                val fields =
                    model.fields.mapIndexed { index, field ->
                        if (field.nullable) errors += Diagnostic(field.position, "Nullable SBE fields are not supported yet.")
                        val fieldMetadata = annotation(field.annotations, "Field", field.position)
                        WireField(field, number(fieldMetadata, "id", field.position), index * 8)
                    }
                if (fields.map { it.id }.distinct().size != fields.size) errors += Diagnostic(model.position, "Duplicate SBE field IDs.")
                if (fields.size * 8 > 65534) errors += Diagnostic(model.position, "Root block exceeds uint16 length.")
                WireMessage(
                    model,
                    number(metadata, "id", model.position),
                    number(metadata, "schemaId", model.position),
                    number(metadata, "version", model.position),
                    fields,
                )
            }
        if (messages.map { it.schemaId to it.templateId }.distinct().size != messages.size) {
            errors += Diagnostic(schema.messages.first().position, "Duplicate schema/template IDs.")
        }
        return LayoutResult(if (errors.isEmpty()) messages else emptyList(), errors)
    }
}
