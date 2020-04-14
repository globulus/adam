package net.globulus.adam.api

interface Value : Expr {
    override fun eval(args: ArgList?): Value {
        return this
    }
}

class Sym(val value: String) : Value, Type {
    override fun toString(): String {
        return "$value\""
    }

    override fun equals(other: Any?): Boolean {
        return value == (other as? Sym)?.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

class Str(val value: String) : Value {
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