package com.example.taxicodegen

import lang.taxi.Compiler
import lang.taxi.types.Annotation
import lang.taxi.types.AnnotationType
import lang.taxi.types.ArrayType
import lang.taxi.types.CompilationUnit
import lang.taxi.types.EnumType
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.SourceNames
import lang.taxi.types.TypeKind

/** The only layer that depends on Taxi compiler types. No wire layout is decided here. */
class TaxiCompilerAdapter {
    fun compile(
        source: String,
        sourceName: String,
    ): CompilationResult {
        val (compilerMessages, document) = Compiler(source, sourceName).compileWithMessages()
        val diagnostics =
            compilerMessages
                .map {
                    Diagnostic(
                        SourcePosition(it.sourceName ?: sourceName, it.line, it.char + 1),
                        it.detailMessage,
                        it.severity.name,
                    )
                }.toMutableList()
        if (diagnostics.any { it.severity == "ERROR" }) return CompilationResult(null, diagnostics)

        fun reject(
            unit: CompilationUnit,
            detail: String,
        ) {
            diagnostics += Diagnostic(unit.position(), detail)
        }

        if (document.services.isNotEmpty() || document.queries.isNotEmpty() || document.expressions.isNotEmpty() ||
            document.policies.isNotEmpty() || document.views.isNotEmpty()
        ) {
            diagnostics += Diagnostic(SourcePosition(sourceName, 1, 1), "Only domain type and annotation declarations are supported.")
        }
        document.functions
            .filter { function ->
                function.compilationUnits.any { SourceNames.normalize(it.source.sourceName) == SourceNames.normalize(sourceName) }
            }.forEach { reject(it.compilationUnits.first(), "Function declarations are not supported.") }
        val enums = mutableListOf<DomainEnum>()
        val messages =
            document.types
                .filter { type ->
                    type.compilationUnits.any { SourceNames.normalize(it.source.sourceName) == SourceNames.normalize(sourceName) }
                }.sortedBy { it.qualifiedName }
                .mapNotNull { type ->
                    if (type is AnnotationType || type is PrimitiveType) return@mapNotNull null
                    if (type is EnumType) {
                        val values = type.values.associate { it.name to (it.value as? Int ?: -1) }
                        if (values.values.any { it !in 0..254 } || values.values.distinct().size != values.size) {
                            reject(
                                type.compilationUnits.first(),
                                "Unsupported type ${type.qualifiedName}: enums need unique explicit uint8 values.",
                            )
                        } else {
                            enums += DomainEnum(type.qualifiedName, values, type.compilationUnits.first().position())
                        }
                        return@mapNotNull null
                    }
                    if (type !is ObjectType) {
                        reject(
                            type.compilationUnits.first(),
                            "Unsupported type ${type.qualifiedName}: ${type.javaClass.simpleName}.",
                        )
                        return@mapNotNull null
                    }
                    val unit = type.compilationUnits.first()
                    if (type.expression != null || type.isPartialType || type.extensions.isNotEmpty()) {
                        reject(unit, "Expressions, partial types, and type extensions are not supported: ${type.qualifiedName}.")
                    }
                    if (type.isScalar && type.typeKind != TypeKind.Model) {
                        if (type.basePrimitive !in setOf(PrimitiveType.LONG, PrimitiveType.STRING)) {
                            reject(unit, "Only Long and String scalar types are supported: ${type.qualifiedName}.")
                        }
                        return@mapNotNull null
                    }
                    if (type.inheritsFrom.any { it is ObjectType }) {
                        reject(unit, "Model inheritance is not supported: ${type.qualifiedName}.")
                    }
                    val fields =
                        type.fields.mapNotNull { field ->
                            val fieldType = field.type
                            val kind =
                                when {
                                    fieldType is EnumType -> {
                                        DomainPrimitive.ENUM
                                    }

                                    fieldType is ArrayType && fieldType.memberType is ObjectType && !fieldType.memberType.isScalar -> {
                                        DomainPrimitive.GROUP
                                    }

                                    fieldType is ObjectType && !fieldType.isScalar -> {
                                        DomainPrimitive.COMPOSITE
                                    }

                                    fieldType.basePrimitive == PrimitiveType.LONG -> {
                                        DomainPrimitive.LONG
                                    }

                                    fieldType.basePrimitive == PrimitiveType.STRING -> {
                                        DomainPrimitive.STRING
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            if (kind == null) {
                                reject(field.compilationUnit, "Unsupported field ${field.name}: ${fieldType.qualifiedName}.")
                                return@mapNotNull null
                            }
                            if (field.accessor != null || field.constraints.isNotEmpty() || field.modifiers.isNotEmpty()) {
                                reject(
                                    field.compilationUnit,
                                    "Computed, constrained, or modified fields are unsupported: ${field.name}.",
                                )
                            }
                            DomainField(
                                field.name,
                                if (fieldType is ArrayType) fieldType.memberType.qualifiedName else fieldType.qualifiedName,
                                kind,
                                field.nullable,
                                field.annotations.map { it.toDomain() },
                                field.compilationUnit.position(),
                            )
                        }
                    DomainMessage(type.qualifiedName, fields, type.annotations.map { it.toDomain() }, unit.position())
                }
        if (messages.isEmpty() && diagnostics.none { it.severity == "ERROR" }) {
            diagnostics += Diagnostic(SourcePosition(sourceName, 1, 1), "Declare at least one domain model.")
        }
        return CompilationResult(
            if (diagnostics.any { it.severity == "ERROR" }) null else DomainSchema(messages, enums),
            diagnostics,
        )
    }
}

private fun CompilationUnit.position() = SourcePosition(source.sourceName, location.line, location.char + 1)

private fun Annotation.toDomain() = DomainAnnotation(qualifiedName, parameters.toSortedMap())
