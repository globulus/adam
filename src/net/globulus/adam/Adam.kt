package net.globulus.adam

import net.globulus.adam.frontend.lexer.Lexer
import net.globulus.adam.frontend.parser.Parser
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
    val source = readFile(args[0])
    val lexer = Lexer(source)
    val tokens = lexer.scanTokens(true)
    val parser = Parser(tokens)
    val exprs = parser.parse()
    for (expr in exprs) {
        println(expr)
    }
}

@Throws(IOException::class)
private fun readFile(path: String): String {
    val bytes = Files.readAllBytes(Paths.get(path))
    return String(bytes, Charset.defaultCharset())
}