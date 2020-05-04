package net.globulus.adam

import net.globulus.adam.frontend.compiler.Compiler
import net.globulus.adam.frontend.compiler.CompilerOutput
import net.globulus.adam.frontend.compiler.Decompiler
import net.globulus.adam.frontend.lexer.Lexer
import net.globulus.adam.frontend.parser.Parser
import net.globulus.adam.frontend.parser.ParserConfig
import net.globulus.adam.frontend.parser.ParserOutput
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
    val output = adam(args[0])
    println(output.byteCode.toHexString())
    println(output.symTable.joinToString())
    println(output.strTable.joinToString())
//    for (expr in output.exprs) {
//        println(expr)
//    }
    println()
    val timestamp = System.currentTimeMillis()
    val decomp = Decompiler(output)
    decomp.decompile()
    mark("Decompiling", timestamp)
}

fun adam(inputPath: String): CompilerOutput {
    var timestamp = System.currentTimeMillis()
    val source = readFile(inputPath)
    timestamp = mark("File read of ${source.length} bytes", timestamp)
    val lexer = Lexer(source)
    val tokens = lexer.scanTokens(true)
    timestamp = mark("Lexing for ${tokens.size} tokens", timestamp)
    val parser = Parser(ParserConfig(), tokens)
    val exprs = parser.parse()
    val output = ParserOutput(parser.currentScope, exprs)
    timestamp = mark("Parsing for ${exprs.size} exprs", timestamp)
    val compiler = Compiler(output)
    val compilerOutput = compiler.compile()
    timestamp = mark("Compiling to ${compilerOutput.byteCode.size} bytes", timestamp)
    return compilerOutput
}

@Throws(IOException::class)
private fun readFile(path: String): String {
    val bytes = Files.readAllBytes(Paths.get(path))
    return String(bytes, Charset.defaultCharset())
}

fun mark(msg: String, timestamp: Long): Long {
    val now = System.currentTimeMillis()
    println("$msg done in ${now - timestamp} ms")
    return now
}

fun ByteArray.toHexString() = joinToString(" ") { "%02x".format(it) }