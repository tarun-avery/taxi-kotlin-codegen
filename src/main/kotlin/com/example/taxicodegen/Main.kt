package com.example.taxicodegen

import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runCli(args, System.out, System.err))
}

fun runCli(
    args: Array<String>,
    out: PrintStream,
    err: PrintStream,
): Int {
    if (args.size != 1 && !(args.size == 3 && args[1] in setOf("--output", "--codecs"))) {
        err.println("Usage: taxi-kotlin-codegen <schema.taxi> [--output|--codecs <directory>]")
        return 2
    }
    val source =
        try {
            Files.readString(Path.of(args[0]))
        } catch (exception: IOException) {
            err.println("Cannot read ${args[0]}: ${exception.message}")
            return 2
        }
    val result = TaxiCompilerAdapter().compile(source, args[0])
    result.diagnostics.forEach(err::println)
    val schema = result.schema ?: return 1
    if (args.size == 3) {
        val generated = if (args[1] == "--codecs") CodecGenerator().generate(schema) else PojoGenerator().generate(schema)
        generated.diagnostics.forEach(err::println)
        if (generated.diagnostics.isNotEmpty()) return 1
        try {
            val outputDirectory = Path.of(args[2])
            generated.sources.forEach { source ->
                val destination = outputDirectory.resolve(source.relativePath)
                Files.createDirectories(destination.parent)
                Files.writeString(destination, source.content)
                out.println("Generated ${source.relativePath}")
            }
        } catch (exception: IOException) {
            err.println("Cannot write generated sources: ${exception.message}")
            return 2
        }
        return 0
    }
    schema.messages.forEach { message ->
        out.println("model ${message.name}")
        message.annotations.forEach { out.println("  @${it.name} ${it.parameters}") }
        message.fields.forEach { field ->
            val nullable = if (field.nullable) "?" else ""
            out.println("  ${field.name}: ${field.semanticType}$nullable -> ${field.primitive}")
            field.annotations.forEach { out.println("    @${it.name} ${it.parameters}") }
        }
    }
    return 0
}
