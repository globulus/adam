package net.globulus.adam.frontend.lexer

import net.globulus.adam.api.Num
import net.globulus.adam.api.Str
import net.globulus.adam.api.Sym
import net.globulus.adam.api.Value
import net.globulus.adam.frontend.Token
import net.globulus.adam.frontend.TokenType

class Lexer(private val source: String) {
    private val sourceLen = source.length
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1

    fun scanTokens(addEof: Boolean): List<Token> {
        while (!isAtEnd) {
            start = current
            scanToken()
        }
        if (addEof) {
            tokens += Token(
                TokenType.EOF,
                "",
                null,
                line
            )
        }
        return tokens
    }

    private fun scanToken() {
        when (val c = advance()) {
            '(' -> addToken(TokenType.LEFT_PAREN)
            ')' -> addToken(TokenType.RIGHT_PAREN)
            '[' -> addToken(TokenType.LEFT_BRACKET)
            ']' -> addToken(TokenType.RIGHT_BRACKET)
            '{' -> addToken(TokenType.LEFT_BRACE)
            '}' -> addToken(TokenType.RIGHT_BRACE)
            ',' -> addToken(TokenType.COMMA)
            '.' -> {
                if (match('.')) {
                    if (match('.')) {
                        addToken(TokenType.THREE_DOTS)
                    } else {
                        addToken(TokenType.TWO_DOTS)
                    }
                } else {
                    addToken(TokenType.DOT)
                }
            }
            '\n' -> {
                line++
                addToken(TokenType.NEWLINE)
            }
            else -> {
                if (isWhiteSpace(c)) {
                    return
                } else if (isStringDelim(c)) {
                    if (matchAll("\"\"")) {
                        comment()
                    } else {
                        string(c)
                    }
                } else if (isDigit(c)) {
                    number()
                } else {
                    identifier()
                }
            }
        }
    }

    private fun comment() {
        if (match('"')) { // Multi line
            while (!matchAll("\"\"\"\"")) {
                if (peek() == '\n') {
                    line++
                }
                advance()
            }
        } else { // Single line
            while (peek() != '\n' && !isAtEnd) {
                advance()
            }
        }
    }

    private fun identifier() {
        while (isValidId(peek())) {
            advance()
        }
        val text = source.substring(start, current)
        addToken(TokenType.SYM, Sym(text))
        if (match('"')) {
            addToken(TokenType.QUOTE)
        }
    }

    private fun number() {
        var isDecimal = false
        while (isDigit(peek())) {
            advance()
        }
        if (peek() == '.' && isDigit(peekNext())) {
            isDecimal = true
            advance()
            while (isDigit(peek())) {
                advance()
            }
        }
        val substr = source.substring(start, current)
        val doubleVal = if (isDecimal) substr.toDouble() else null
        val longVal = if (isDecimal) null else substr.toLong()
        addToken(TokenType.NUM, Num(doubleVal, longVal))
    }

    private fun string(opener: Char) {
        while (peek() != opener && !isAtEnd) {
            if (peek() == '\n') {
                line++
            }
            advance()
        }
        if (isAtEnd) {
            error(line, "Unterminated string")
            return
        }
        advance()
        val value = escapedString(start + 1, current - 1)
        addToken(TokenType.STR, Str(value))
    }

    private fun escapedString(start: Int, stop: Int) = source.substring(start, stop)

    private fun match(expected: Char): Boolean {
        if (isAtEnd) {
            return false
        }
        if (peek() != expected) {
            return false
        }
        current++
        return true
    }

    private fun matchAll(expected: String): Boolean {
        val end = current + expected.length
        if (end >= sourceLen) {
            return false
        }
        if (source.substring(current, end) != expected) {
            return false
        }
        current = end
        return true
    }

    private fun peek(): Char {
        if (isAtEnd) {
            return NULL_CHAR
        }
        return source[current]
    }

    private fun peekNext(): Char {
        if (current + 1 >= sourceLen) {
            return NULL_CHAR
        }
        return source[current + 1]
    }

    private fun isAlpha(c: Char) = c.isLetter() || c == '_' || c == '$'

    private fun isDigit(c: Char) = c.isDigit()

    private fun isValidId(c: Char) = !isWhiteSpace(c) && c !in NON_ID_CHARS

    private fun isStringDelim(c: Char) = c == '"'

    private fun isWhiteSpace(c: Char) = c in WHITESPACE_CHARS

    private val isAtEnd get() = current >= sourceLen

    private fun advance(): Char {
        current++
        return source[current - 1]
    }

    private fun addToken(type: TokenType, literal: Value? = null) {
        val text = source.substring(start, current)
        tokens += Token(type, text, literal, line)
    }

    private fun error(line: Int, message: String) {
        throw LexException(line, message)
    }

    companion object {
        private const val NULL_CHAR = '\u0000'
        private const val NON_ID_CHARS = "()[]{},.\""
        private const val WHITESPACE_CHARS = " \t\n"
    }
}