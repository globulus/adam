package net.globulus.adam.frontend.lexer

class LexException(line: Int?,
                   message: String
) : Exception("Scan exception @${line ?: -1}: $message.")