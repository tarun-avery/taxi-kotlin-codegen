package com.example.taxicodegen

/** Fixed composite layouts and a root tail consisting of groups followed by UTF-8 data. */
data class RichField(
    val field: DomainField,
    val offset: Int,
    val size: Int,
)

data class RichRecord(
    val model: DomainMessage,
    val fields: List<RichField>,
    val blockLength: Int,
)

data class RichPlan(
    val records: Map<String, RichRecord>,
    val roots: List<WireMessage>,
    val diagnostics: List<Diagnostic>,
)

class RichLayoutPlanner {
    fun plan(schema: DomainSchema): RichPlan {
        val errors = mutableListOf<Diagnostic>()
        val models = schema.messages.associateBy { it.name }
        val records = linkedMapOf<String, RichRecord>()
        val visiting = mutableSetOf<String>()

        fun reject(
            model: DomainMessage,
            text: String,
        ) {
            errors += Diagnostic(model.position, text)
        }

        fun record(name: String): RichRecord? {
            records[name]?.let { return it }
            val model = models[name] ?: return null
            if (!visiting.add(name)) {
                reject(model, "Recursive composites are unsupported.")
                return null
            }
            var offset = 0
            var tail = false
            var dataSeen = false
            val ids = mutableSetOf<Int>()
            val fields =
                model.fields.map { field ->
                    val metadata = field.annotations.filter { it.name.substringAfterLast('.') == "Field" }
                    val rawId = metadata.singleOrNull()?.parameters?.get("id")
                    val id = (rawId as? Number)?.toInt()
                    if (id == null || rawId.toString() != id.toString() || id !in 0..65534 ||
                        !ids.add(id)
                    ) {
                        reject(model, "Invalid or duplicate field ID: ${field.name}.")
                    }
                    if (field.annotations.any {
                            it.name.substringAfterLast(
                                '.',
                            ) == "Since"
                        }
                    ) {
                        reject(model, "Rich schema evolution is not supported yet.")
                    }
                    if (field.nullable && field.primitive !in setOf(DomainPrimitive.LONG, DomainPrimitive.ENUM)) {
                        reject(model, "Only primitive and enum fields may be nullable.")
                    }
                    if (field.annotations.any { it.name.substringAfterLast('.') == "FullRange" }) {
                        reject(model, "@FullRange is supported only in the fixed Long profile.")
                    }
                    val size =
                        when (field.primitive) {
                            DomainPrimitive.LONG -> {
                                8
                            }

                            DomainPrimitive.ENUM -> {
                                1
                            }

                            DomainPrimitive.COMPOSITE, DomainPrimitive.GROUP -> {
                                val nested = record(field.semanticType)
                                if (nested == null ||
                                    nested.fields.any { it.size == 0 }
                                ) {
                                    reject(model, "Nested records must have a fixed layout.")
                                }
                                if (field.primitive == DomainPrimitive.GROUP) 0 else nested?.blockLength ?: 0
                            }

                            DomainPrimitive.STRING -> {
                                0
                            }
                        }
                    if (size == 0) {
                        tail = true
                        if (field.primitive == DomainPrimitive.STRING) dataSeen = true
                        if (field.primitive == DomainPrimitive.GROUP && dataSeen) reject(model, "Groups must precede variable data.")
                    } else if (tail) {
                        reject(model, "Fixed fields must precede groups and variable data.")
                    }
                    RichField(field, offset, size).also { offset += size }
                }
            if (offset > 65534) reject(model, "Root/composite block exceeds uint16 length.")
            visiting.remove(name)
            return RichRecord(model, fields, offset).also { records[name] = it }
        }
        schema.messages.forEach { record(it.name) }
        val roots = schema.messages.filter { model -> model.annotations.any { it.name.substringAfterLast('.') == "Message" } }
        records.values.filter { it.model !in roots && it.fields.any { field -> field.size == 0 } }.forEach {
            reject(it.model, "Non-message records must have a fixed layout.")
        }
        // Reuse header/ID validation without assigning the rich layout's offsets twice.
        val headerModels = roots.map { it.copy(fields = emptyList()) }
        val headers = WireLayoutPlanner().plan(DomainSchema(headerModels))
        errors += headers.diagnostics
        if (roots.isEmpty()) errors += Diagnostic(schema.messages.first().position, "At least one @Message is required for codecs.")
        headers.messages.filter { it.version != 0 }.forEach { reject(it.model, "Rich codecs currently require schema version 0.") }
        return RichPlan(records, headers.messages, errors)
    }
}
