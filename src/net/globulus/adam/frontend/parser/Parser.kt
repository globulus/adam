package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*
import net.globulus.adam.frontend.Token
import net.globulus.adam.frontend.TokenType
import java.util.*

class Parser(private val tokens: List<Token>) {

    private var current = 0

    private val scopeStack = Stack<Scope>()
    private val currentScope get() = scopeStack.peek()

    init {
        // Init root scope and add primitive types to it
        scopeStack += Scope(null)
    }

    fun parse(): List<Expr> {
        val exprs = mutableListOf<Expr>()
        matchAllNewlines()
        while (!isAtEnd) {
            stmtLine()?.let {
                exprs += it
            }
            matchAllNewlines()
        }
        return exprs
    }

    private fun stmtLine() = stmt(false, TokenType.NEWLINE)

    private fun valueLine(delimiter: TokenType) = stmt(true, TokenType.COMMA, delimiter)

    private fun stmt(rollBack: Boolean, vararg delimiters: TokenType): Expr? {
        val exprs = mutableListOf<Expr>()
        while (!isAtEnd && !match(*delimiters)) {
            // Check if the line is a typedef or an expr
            if (typedef()) {
                continue
            }
            exprs += grouping()
        }
        if (rollBack) {
            rollBack()
        }
        if (exprs.isEmpty()) {
            return null
        }
        ParserLog.v("Parsed on line $exprs")
        val desugared = DesugarDaddy.hustle(currentScope, exprs)
        ParserLog.ds("Desugared $desugared")
        if (desugared.size != 1) {
            throw ParseException(previous, "More than one expression on a line, desugaring failed")
        }
        return desugared[0]
    }

    private fun typedef(): Boolean {
        if (peek.literal == TYPEDEF_START) {
            advance()
            val sym = consumeSym("Need Sym for typedef")
            val type = type()
            consume(TokenType.NEWLINE, "Need newline after typedef")
            ParserLog.v("Set type alias for $sym as $type")
            currentScope.typeAliases.putIfAbsent(sym, type)
            return true
        }
        return false
    }

    private fun type(): Type {
        val type: Type = if (match(TokenType.SYM)) {
            previousSym
        } else {
            structListOrBlockdef()
        }
        return if (match(TokenType.THREE_DOTS)) {
            Vararg(type)
        } else {
            type
        }
    }

    private fun structListOrBlockdef(): Type {
        val gens = genList()
        if (gens != null && !match(TokenType.TWO_DOTS)) {
            return gens.asStructList()
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
            if (!check(TokenType.RIGHT_BRACKET, TokenType.COMMA, TokenType.NEWLINE)) {
                expr = grouping()
            }
            StructList.Prop(type, sym, expr)
        })
    }

    private fun genList(): GenList? {
        return if (check(TokenType.LEFT_BRACKET)) {
            GenList(parseListWithAtLeastOneElement("gen") {
                var type: Type? = type()
                val sym: Sym
                if (type is Sym) {
                    if (match(TokenType.SYM)) {
                        sym = previousSym
                    } else {
                        sym = type
                        type = null
                    }
                } else {
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
            valueLine(TokenType.RIGHT_PAREN)!!
        } else {
            value(true)
        }
        if (hasOpeningParen) {
            consume(TokenType.RIGHT_PAREN, "Need ) at end of grouping")
        }
        return expr
    }

    private fun value(primaryGetter: Boolean): Expr {
        val getter = getter(primaryGetter)
        return if (peek.type == TokenType.LEFT_PAREN) {
            val args = argList()
            val call = Call(currentScope, getter, args)
            if (match(TokenType.DOT)) {
                when (val next = value(false)) {
                    is Getter -> call + next
                    is Call -> call + next
                    else -> throw ParseException(previous, "Next chain element must be a Getter or a Call, instead it's $next")
                }
            } else {
                call
            }
        } else {
            getter
        }
    }

    /**
     * Primary getter has a primitive origin, while a secondary can only
     * have a Sym origin.
     */
    private fun getter(primary: Boolean): Getter {
        val origin = if (primary) {
            primitive()
        } else {
            consumeSym("Expected a Sym for secondary getter").patchType(currentScope, false)
        }
        val syms = mutableListOf<Sym>()
        while (match(TokenType.DOT)) {
            syms += consumeSym("Expected a Sym after . in Getter")
        }
        return Getter(currentScope, origin, syms)
    }

    private fun primitive(): Expr {
        if (match(TokenType.SYM)) {
            return previousSym.patchType(currentScope, true)
        }
        if (match(TokenType.NUM, TokenType.STR)) {
            return previous.literal as Expr
        }
        return when (peek.type) {
            TokenType.LEFT_BRACKET -> structListOrRawList()
            TokenType.LEFT_BRACE -> block()
            TokenType.LEFT_PAREN -> grouping()
            else -> throw ParseException(peek, "Invalid value")
        }
    }

    private fun structListOrRawList(): AdamList<*> {
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
        return RawList(currentScope, parseRawOrArgList("raw", TokenType.LEFT_BRACKET, TokenType.RIGHT_BRACKET))
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

    private fun block(): Block {
        consume(TokenType.LEFT_BRACE, "Expected { at block start")
        var args: StructList? = null
        var ret: Type? = null
        if (peek.type == TokenType.LEFT_BRACKET) { // Has def in first line
            args = structList()
            if (!match(TokenType.NEWLINE)) {
                ret = type()
                consume(TokenType.NEWLINE, "Expected newline after block def inside block")
            }
        }
        val body = mutableListOf<Expr>()
        val bodyScope = Scope(currentScope).apply {
            args?.let {
                typeAliases.putAll(args.props.map { it.sym to it.type })
            }
        }
        scopeStack.push(bodyScope)
        while (!match(TokenType.RIGHT_BRACE)) {
            body += stmtLine()!!
        }
        if (ret == null) {
            ret = TypeInfernal.infer(bodyScope, body)
        }
        return Block(args, ret, body)
    }

    private fun argList(): ArgList {
        return ArgList(currentScope, parseRawOrArgList("arg", TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN))
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
        private val TYPEDEF_START = Sym("adam")
    }
}