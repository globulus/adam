package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*
import net.globulus.adam.frontend.Token
import net.globulus.adam.frontend.TokenType
import java.util.*

class Parser(private val tokens: List<Token>) {

    private var current = 0

    private val scopeStack = Stack<Scope>()
    private val currentScope get() = scopeStack.peek()

    private var currentlyDefinedType: CurrentlyDefinedType? = null

    init {
        // Init root scope and add primitive types to it
        scopeStack += Scope(null)
    }

    fun parse(): List<Expr> {
        val exprs = mutableListOf<Expr>()
        matchAllNewlines()
        while (!isAtEnd) {
            stmtLine()?.let { exprs += it }
            matchAllNewlines()
        }
        return exprs
    }

    private fun stmtLine(): Expr? {
        val stmtsExprs =  stmt(false, TokenType.NEWLINE)
        return if (stmtsExprs.isNotEmpty()) {
            if (stmtsExprs.size != 1) {
                throw ParseException(previous, "More than one expression on a line, desugaring failed")
            }
            stmtsExprs[0]
        } else {
            null
        }
    }

    private fun valueLine(delimiter: TokenType): Expr {
        val exprs = stmt(true, TokenType.COMMA, TokenType.NEWLINE, delimiter)
        if (exprs.size != 1) {
            throw ParseException(previous, "More than one expression in a grouping, desugaring failed")
        }
        return exprs[0]
    }

    private fun stmt(rollBack: Boolean, vararg delimiters: TokenType): List<Expr> {
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
        ParserLog.v("Parsed on line $exprs")
        val desugared = DesugarDaddy.hustle(currentScope, exprs)
        ParserLog.ds("Desugared $desugared")
        return desugared
    }

    private fun typedef(): Boolean {
        if (peek.literal == TYPEDEF_START) {
            advance()
            val sym = consumeSym("Need Sym for typedef")
            currentlyDefinedType = CurrentlyDefinedType(sym)
            val type = type().apply { alias = sym }
            currentlyDefinedType = null
            consume(TokenType.NEWLINE, "Need newline after typedef")
            ParserLog.v("Set type alias for $sym as $type")
            currentScope.typeAliases.putIfAbsent(sym, type)
            return true
        }
        return false
    }

    private fun type(): Type {
        val type: Type = if (match(TokenType.SYM)) {
            symTypeWithPatchedGens(previousSym)
        } else {
            structListOrBlockdef()
        }
        return if (match(TokenType.THREE_DOTS)) {
            Vararg(type)
        } else {
            type
        }
    }

    private fun symTypeWithPatchedGens(sym: Sym): Type {
        val typeGens = if (sym == currentlyDefinedType?.sym) {
            currentlyDefinedType!!.gens
        } else {
            TypeInfernal.infer(currentScope, sym, true).let {
                when (it) {
                    is Blockdef -> it.gens
                    is StructList -> it.gens
                    else -> null
                }
            }
        }
        if (match(TokenType.TWO_DOTS)) {
            val genList = rawList()
            sym.gens = genList.props.map { it.sym ?: it.expr as Sym }
            if (typeGens?.props?.size ?: 0 != sym.gens!!.size) {
                throw ParseException(previous, "Supplied gens table doesn't match in arity with Blockdef gens table!")
            }
        } else if (typeGens != null) {
            throw ParseException(previous, "This sym evaluates to a type that has gens, yet none were specified!")
        }
        return sym
    }

    private fun structListOrBlockdef(): Type {
        val gens = genList()
        if (gens != null && !match(TokenType.TWO_DOTS)) {
            return gens.asStructList()
        }
        currentlyDefinedType?.setGensIfNull(gens)
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
        return structList(gens)
    }

    private fun structList(gens: GenList? = null): StructList {
        return StructList(gens, parseListWithAtLeastOneElement("struct") {
            val type = type().apply {
                if (this is Blockdef) {
                    this.gens = gens
                } else if (this is StructList) {
                    this.gens = gens
                }
            }
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
        val expr = if (hasOpeningParen) {
            valueLine(TokenType.RIGHT_PAREN)
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
        return when (peek.type) {
            TokenType.LEFT_PAREN -> callOrGetterPlusGrouping(getter)
            else -> getter.unpacked
        }
    }

    private fun callOrGetterPlusGrouping(getter: Getter): Expr {
        val storedCurrent = current
        val args = try {
            argList()
        } catch (e: ParseException) { // Let's try it as a value line
            current = storedCurrent
            val expr = valueLine(TokenType.RIGHT_PAREN)
            ArgList(currentScope, listOf(RawList.Prop(expr)))
        }
        try {
            val call = Call(currentScope, getter, args).validate()
            return if (match(TokenType.DOT)) {
                val next = getter(false)
                val comboOp = call + next
                callOrGetterPlusGrouping(comboOp)
            } else {
                call
            }
        } catch (e: ValidationException) {
            ParserLog.v("Validation exception thrown while validation a call: ${e.message}")
            // TODO optimize, return both getter and grouping at the same time since both are known at this point
            if (args.props.size == 1) { // This might be a getter + grouping, return getter and reset current
                current = storedCurrent
                return getter.unpacked
            } else {
                throw e
            }
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
        return Getter(origin, syms).patchType(currentScope, false)
    }

    private fun primitive(): Expr {
        if (match(TokenType.SYM)) {
            return previousSym.patchType(currentScope, true)
        }
        if (match(TokenType.NUM, TokenType.STR)) {
            return (previous.literal as Expr).apply {
                type = TypeInfernal.infer(currentScope, this)
            }
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
            structList(null)
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
            args = structList(null)
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
        if (args == null && !match(TokenType.NEWLINE)) {
            body += valueLine(TokenType.RIGHT_BRACE)
            consume(TokenType.RIGHT_BRACE, "Expected } after value in a lambda block!")
        } else {
            while (!match(TokenType.RIGHT_BRACE)) {
                stmtLine()?.let { body += it }
            }
        }
        if (ret == null) {
            ret = TypeInfernal.infer(bodyScope, body)
        }
        scopeStack.pop()
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
            current++
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

    /**
     * If a type is using itself recursively, such as ifBranching
     */
    private class CurrentlyDefinedType(val sym: Sym) {
        var gens: GenList? = null
            private set

        fun setGensIfNull(gens: GenList?) {
            if (this.gens == null) {
                this.gens = gens
            }
        }
    }

    companion object {
        private val TYPEDEF_START = Sym("adam")
    }
}