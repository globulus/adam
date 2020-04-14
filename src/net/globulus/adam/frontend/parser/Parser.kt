package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*
import net.globulus.adam.frontend.Token
import net.globulus.adam.frontend.TokenType
import java.util.*

class Parser(private val tokens: List<Token>) {

    private var current = 0
    private val scopeStack = Stack<Scope>()

    // Primitive types
    private val symType = Sym("Sym")
    private val numType = Sym("Num")
    private val strType = Sym("Str")

    init {
        // Init root scope and add primitive types to it
        scopeStack += Scope(null).apply {
            syms.apply {
                add(symType)
                add(numType)
                add(strType)
            }
            types.apply {
                add(symType)
                add(numType)
                add(strType)
            }
            typeAliases.apply {
                put(symType, symType)
                put(numType, numType)
                put(strType, strType)
            }
        }
    }

    fun parse(): List<Expr> {
        val exprs = mutableListOf<Expr>()
        matchAllNewlines()
        while (!isAtEnd) {
            exprs.addAll(stmtLine())
            matchAllNewlines()
        }
        return exprs
    }

    private fun stmtLine() = stmt(false, TokenType.NEWLINE)

    private fun valueLine(delimiter: TokenType) = stmt(true, TokenType.COMMA, delimiter)

    private fun stmt(rollBack: Boolean, vararg delimiters: TokenType): List<Expr> {
        val exprs = mutableListOf<Expr>()
        while (!match(*delimiters)) {
            // Check if the line is a typedef or an expr
            if (typedef()) {
                continue
            }
            exprs += grouping()
        }
        if (rollBack) {
            rollBack()
        }
        ParserLog.v("Parsed on line $exprs")
        // TODO desugar
        ParserLog.ds("Desugared $exprs")
        return exprs
    }

    private fun typedef(): Boolean {
        if (peek.literal == TYPEDEF_START) {
            advance()
            val sym = consumeSym("Need Sym for typedef")
            val type = type()
            consume(TokenType.NEWLINE, "Need newline after typedef")
            ParserLog.v("Set type alias for $sym as $type")
            scopeStack.peek().typeAliases.putIfAbsent(sym, type)
            return true
        }
        return false
    }

    private fun type(): Type {
        if (match(TokenType.SYM)) {
            return previousSym
        }
        return structListOrBlockdef()
    }

    private fun structListOrBlockdef(): Type {
        val gens = genList()
        if (gens != null) {
            consume(TokenType.TWO_DOTS, "Expected .. after gen list")
        }
        var rec: Sym? = null
        if (match(TokenType.SYM)) {
            rec = previousSym
            consume(TokenType.DOT, "Expected . after rec")
        }
        if (match(TokenType.LEFT_BRACE)) {
            var args: StructList? = null
            if (peek.type == TokenType.LEFT_BRACKET) {
                args = structList()
            }
            val ret = type()
            consume(TokenType.RIGHT_BRACE, "Expected } at the end of blockdef")
            return Blockdef(gens, rec, args, ret)
        }
        return structList()
    }

    private fun structList(): StructList {
        return StructList(parseListWithAtLeastOneElement("struct") {
            val type = type()
            val sym = consumeSym("Expected Sym after type in struct list prop")
            var expr: Expr? = null
            if (!check(TokenType.RIGHT_BRACKET, TokenType.COMMA)) {
                expr = grouping()
            }
            StructList.Prop(type, sym, expr)
        })
    }

    private fun genList(): GenList? {
        return if (check(TokenType.LEFT_BRACKET)) {
            GenList(parseListWithAtLeastOneElement("gen") {
                var type: Type? = null
                var sym: Sym
                if (match(TokenType.SYM)) {
                    sym = previousSym
                    if (match(TokenType.SYM)) {
                        type = sym
                        sym = previousSym
                    }
                } else {
                    type = type()
                    sym = consumeSym("Expected Sym after type in gen list prop")
                }
                GenList.Prop(type, sym)
            })
        } else {
            null
        }
    }

    private fun <T> parseListWithAtLeastOneElement(tag: String, propParser: () -> T): List<T> {
        consume(TokenType.LEFT_BRACKET, "Expected [ at start of $tag list")
        matchAllNewlines()
        val props = mutableListOf<T>()
        do {
            matchAllNewlines()
            props += propParser()
            matchAllNewlines()
            if (match(TokenType.RIGHT_BRACKET)) {
                break
            }
            consume(TokenType.COMMA, "Expected , as $tag list prop separator")
        } while (true)
        return props
    }

    private fun grouping(): Expr {
        val hasOpeningParen = match(TokenType.LEFT_PAREN)
        val expr = if (peek.type == TokenType.LEFT_PAREN) {
            grouping()
        } else {
            value()
        }
        if (hasOpeningParen) {
            consume(TokenType.RIGHT_PAREN, "Need ) at end of grouping")
        }
        return expr
    }

    private fun value(): Expr {
        if (match(TokenType.SYM, TokenType.NUM, TokenType.STR)) {
            return previous.literal as Expr
        }
        return when (peek.type) {
            TokenType.LEFT_BRACKET -> structListOrRawList()
            TokenType.LEFT_BRACE -> blockOrCall()
            TokenType.LEFT_PAREN -> grouping()
            else -> throw ParseException(peek, "Invalid value")
        }
    }

    private fun structListOrRawList(): AdamList {
        // Differentiate raw from struct list by counting the number of tokens
        // before the first comma or right bracket - if it's > 1, it's a struct list
        var tokenCount = 0
        while (true) {
            val token = tokens[current + 1 + tokenCount] // +1 to skip initial [
            if (token.type == TokenType.COMMA || token.type == TokenType.RIGHT_BRACKET) {
                break
            }
            tokenCount++
        }
        return if (tokenCount > 1) {
            structList()
        } else {
            rawList()
        }
    }

    private fun rawList(): RawList {
        return RawList(parseRawOrArgList("raw", TokenType.LEFT_BRACKET, TokenType.RIGHT_BRACKET))
    }

    private fun parseRawOrArgList(tag: String,
                                  opener: TokenType,
                                  terminator: TokenType
    ): List<RawList.Prop> {
        consume(opener, "Expected $opener at start of $tag list")
        matchAllNewlines()
        val props = mutableListOf<RawList.Prop>()
        while (true) {
            matchAllNewlines()
            if (match(terminator)) {
                break
            }
            var sym: Sym? = null
            val expr: Expr
            if (match(TokenType.SYM)) {
                if (check(terminator, TokenType.COMMA)) {
                    expr = previousSym
                } else {
                    sym = previousSym
                    expr = grouping()
                }
            } else {
                expr = grouping()
            }
            props += RawList.Prop(sym, expr)
            matchAllNewlines()
            if (match(terminator)) {
                break
            }
            consume(TokenType.COMMA, "Expected , as $tag list prop separator")
        }
        return props
    }

    private fun blockOrCall(): Expr {
        val block = block()
        return if (peek.type != TokenType.LEFT_PAREN) {
            block
        } else {
            call(block)
        }
    }

    private fun block(): Block {
        consume(TokenType.LEFT_BRACE, "Expected { at block start")
        var args: StructList? = null
        var ret: Type? = null
        if (peek.type == TokenType.LEFT_BRACKET) { // Has def in first line
            args = structList()
            ret = type()
            consume(TokenType.NEWLINE, "Expected newline after block def inside block")
        }
        val body = mutableListOf<Expr>()
        scopeStack.push(Scope(scopeStack.peek()))
        while (!match(TokenType.RIGHT_BRACE)) {
            body += stmtLine()
        }
        return Block(args, ret, body)
    }

    private fun call(defaultBlock: Block? = null): Call {
        val op: Expr = defaultBlock
            ?: if (peek.type == TokenType.LEFT_BRACE) {
                block()
            } else {
                getter()
            }
        val args = argList()
        return Call(op, args)
    }

    private fun getter(): Expr {
        val syms = PriorityQueue<Sym>()
        syms.add(consumeSym("Expected at least one Sym for Getter"))
        while (match(TokenType.DOT)) {
            syms.add(consumeSym("Expected Sym after . in Getter"))
        }
        return Getter(syms)
    }

    private fun argList(): ArgList {
        return ArgList(parseRawOrArgList("arg", TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN))
    }

    private fun matchAllNewlines() {
        while (match(TokenType.NEWLINE)) {
            // just let is pass
        }
    }

    private fun match(vararg types: TokenType): Boolean {
        if (check(*types)) {
            advance()
            return true
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) {
            return advance()
        }
        throw ParseException(peek, message)
    }

    private fun consumeSym(message: String) = consume(TokenType.SYM, message).literal as Sym

    private fun check(vararg types: TokenType): Boolean {
        if (isAtEnd) {
            return false
        }
        for (type in types) {
            if (peek.type == type) {
                return true
            }
        }
        return false
    }

    private fun advance(): Token {
        if (!isAtEnd) {
            current += 1
        }
        return previous
    }

    private fun rollBack() {
        current -= 1
    }

    private val isAtEnd get() = peek.type == TokenType.EOF

    private val peek get() = tokens[current]

    private val previous get() = tokens[current - 1]

    private val previousSym get() = previous.literal as Sym

    private fun peekSequence(vararg types: TokenType): Boolean {
        if (current + types.size >= tokens.size) {
            return false
        }
        for (i in types.indices) {
            if (tokens[current + i].type != types[i]) {
                return false
            }
        }
        return true
    }

    companion object {
        private val TYPEDEF_START = Str("adam")
    }
}