package net.globulus.adam.api

import net.globulus.adam.frontend.parser.Scope

interface Value : Expr, Type {
    override fun eval(args: ArgList?): Value {
        return this
    }
}

class Sym(val value: String) : Value {
    override var type: Type? = this

    override fun toString(): String {
        return value
    }

    override fun equals(other: Any?): Boolean {
        return value == (other as? Sym)?.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    fun patchType(scope: Scope): Sym {
//        type = TypeInfernal.infer(scope, this)
        return this
    }

    companion object {
        val EMPTY = Sym("")
    }
}

class Str(val value: String) : Value {
    override var type: Type? = this

    override fun toString(): String {
        return "\"$value\""
    }

    override fun equals(other: Any?): Boolean {
        return value == (other as? Str)?.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

class Num(val value: Double) : Value {
    override var type: Type? = this

    override fun toString(): String {
        return value.toString()
    }

    override fun equals(other: Any?): Boolean {
        return value == (other as? Num)?.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}