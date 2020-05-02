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
    val decomp = Decompiler(output)
    decomp.decompile()
}

fun adam(inputPath: String): CompilerOutput {
    val source = readFile(inputPath)
    val lexer = Lexer(source)
    val tokens = lexer.scanTokens(true)
    val parser = Parser(ParserConfig(), tokens)
    val exprs = parser.parse()
    val output = ParserOutput(parser.currentScope, exprs)
    val compiler = Compiler(output)
    return compiler.compile()
}

@Throws(IOException::class)
private fun readFile(path: String): String {
    val bytes = Files.readAllBytes(Paths.get(path))
    return String(bytes, Charset.defaultCharset())
}

fun ByteArray.toHexString() = joinToString(" ") { "%02x".format(it) }