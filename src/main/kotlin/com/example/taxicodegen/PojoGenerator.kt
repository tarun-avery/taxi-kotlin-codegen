package com.example.taxicodegen

data class GeneratedSource(
    val relativePath: String,
    val content: String,
)

data class GenerationResult(
    val sources: List<GeneratedSource>,
    val diagnostics: List<Diagnostic>,
)

/** Pure source generation: no filesystem access and no dependency on Taxi compiler types. */
class PojoGenerator {
    fun generate(schema: DomainSchema): GenerationResult {
        val diagnostics = mutableListOf<Diagnostic>()
        val sources =
            schema.messages.sortedBy { it.name }.mapNotNull { message ->
                val parts = message.name.split('.')
                val names = parts + message.fields.map { it.name }
                if (names.any { !IDENTIFIER.matches(it) }) {
                    diagnostics += Diagnostic(message.position, "Kotlin generation requires ASCII identifiers: ${message.name}.")
                    return@mapNotNull null
                }
                if (message.fields.isEmpty()) {
                    diagnostics += Diagnostic(message.position, "A data class requires at least one field: ${message.name}.")
                    return@mapNotNull null
                }
                val packageParts = parts.dropLast(1)
                val className = parts.last() + "Pojo"
                val content =
                    buildString {
                        appendLine("// Generated from Taxi. Do not edit.")
                        if (packageParts.isNotEmpty()) {
                            appendLine("package ${packageParts.joinToString(".") { identifier(it) }}")
                        }
                        appendLine()
                        appendLine("data class $className(")
                        message.fields.forEach { field ->
                            val type = field.kotlinType()
                            appendLine("    val ${identifier(field.name)}: $type${if (field.nullable) "?" else ""},")
                        }
                        appendLine(")")
                    }
                GeneratedSource((packageParts + "$className.kt").joinToString("/"), content)
            }
        val enumSources =
            schema.enums.mapNotNull { definition ->
                if ((definition.name.split('.') + definition.values.keys).any { !IDENTIFIER.matches(it) }) {
                    diagnostics += Diagnostic(definition.position, "Enum names must be ASCII identifiers.")
                    return@mapNotNull null
                }
                val namespace = definition.name.substringBeforeLast('.', "")
                val name = definition.name.substringAfterLast('.')
                val content =
                    buildString {
                        if (namespace.isNotEmpty()) appendLine("package ${namespace.split('.').joinToString(".") { identifier(it) }}")
                        appendLine()
                        appendLine("enum class ${identifier(name)}(val wireValue: Int) {")
                        definition.values.forEach { (key, code) -> appendLine("    ${identifier(key)}($code),") }
                        appendLine("}")
                    }
                GeneratedSource(definition.name.replace('.', '/') + ".kt", content)
            }
        val allSources = sources + enumSources
        val collisions = allSources.groupBy { it.relativePath.lowercase(java.util.Locale.ROOT) }.values.filter { it.size > 1 }
        if (collisions.isNotEmpty()) {
            diagnostics +=
                Diagnostic(
                    schema.messages.first().position,
                    "Generated file names collide on case-insensitive filesystems.",
                )
        }
        return GenerationResult(if (diagnostics.isEmpty()) allSources else emptyList(), diagnostics)
    }

    private fun identifier(value: String) = if (value in KEYWORDS) "`$value`" else value

    companion object {
        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val KEYWORDS =
            setOf(
                "as",
                "break",
                "class",
                "continue",
                "do",
                "else",
                "false",
                "for",
                "fun",
                "if",
                "in",
                "interface",
                "is",
                "null",
                "object",
                "package",
                "return",
                "super",
                "this",
                "throw",
                "true",
                "try",
                "typealias",
                "typeof",
                "val",
                "var",
                "when",
                "while",
                "_",
            )
    }
}
