package net.globulus.adam.frontend

import net.globulus.adam.api.Value

data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Value?,
    val line: Int
) {
    override fun toString(): String {
        return "[$type, $lexeme, $literal, @$line]"
    }
}