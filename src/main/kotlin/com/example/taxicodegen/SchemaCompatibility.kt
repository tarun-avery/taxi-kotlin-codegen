package com.example.taxicodegen

/** Checks wire identity and append-only evolution against a preserved schema. */
class SchemaCompatibility {
    fun check(
        previous: DomainSchema,
        current: DomainSchema,
    ): List<Diagnostic> {
        if ((previous.messages + current.messages).any { model -> model.fields.any { it.primitive != DomainPrimitive.LONG } }) {
            return listOf(Diagnostic(current.messages.first().position, "Rich schema compatibility is not supported yet."))
        }
        val old = WireLayoutPlanner().plan(previous)
        val new = WireLayoutPlanner().plan(current)
        val errors = (old.diagnostics + new.diagnostics).toMutableList()
        if (errors.isNotEmpty()) return errors
        old.messages.forEach { before ->
            val after = new.messages.find { it.schemaId == before.schemaId && it.templateId == before.templateId }

            fun reject(message: String) {
                errors += Diagnostic(before.model.position, message)
            }
            if (after == null) {
                reject("A schema/template identity was removed or changed.")
                return@forEach
            }
            if (after.version < before.version) reject("Schema version cannot decrease.")
            before.fields.forEachIndexed { index, field ->
                val next = after.fields.getOrNull(index)
                if (next == null || next.id != field.id || next.offset != field.offset ||
                    next.field.name != field.field.name || next.field.primitive != field.field.primitive ||
                    next.field.nullable != field.field.nullable || next.sinceVersion != field.sinceVersion
                ) {
                    reject("Existing field ${field.field.name} was removed or changed incompatibly.")
                }
            }
            after.fields.drop(before.fields.size).forEach { field ->
                if (after.version <= before.version || field.sinceVersion <= before.version || !field.field.nullable) {
                    reject("New field ${field.field.name} requires a newer version and optional encoding.")
                }
            }
        }
        return errors
    }
}
