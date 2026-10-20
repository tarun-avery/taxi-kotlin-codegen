package com.example.taxicodegen

data class SourcePosition(
    val source: String,
    val line: Int,
    val column: Int,
)

data class Diagnostic(
    val position: SourcePosition,
    val message: String,
    val severity: String = "ERROR",
) {
    override fun toString(): String = "${position.source}:${position.line}:${position.column}: $severity: $message"
}

data class DomainAnnotation(
    val name: String,
    val parameters: Map<String, Any?>,
)

enum class DomainPrimitive { LONG }

data class DomainField(
    val name: String,
    val semanticType: String,
    val primitive: DomainPrimitive,
    val nullable: Boolean,
    val annotations: List<DomainAnnotation>,
    val position: SourcePosition,
)

data class DomainMessage(
    val name: String,
    val fields: List<DomainField>,
    val annotations: List<DomainAnnotation>,
    val position: SourcePosition,
)

data class DomainSchema(
    val messages: List<DomainMessage>,
)

data class CompilationResult(
    val schema: DomainSchema?,
    val diagnostics: List<Diagnostic>,
)
