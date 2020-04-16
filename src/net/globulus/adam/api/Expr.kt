package net.globulus.adam.api

import net.globulus.adam.frontend.parser.Scope
import net.globulus.adam.frontend.parser.TypeInfernal

interface Expr {
    var type: Type?
    fun eval(args: ArgList? = null): Value
}

class Block(val args: StructList?,
            val ret: Type,
            val body: List<Expr>) : Expr {
    override var type: Type? = ret

    override fun eval(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return StringBuilder().apply {
            append("{")
            args?.let {
                append(args.toString())
            }
            append(type.toString())
            append("\n")
            for (line in body) {
                append(body.toString())
                append("\n")
            }
            append("}")
        }.toString()
    }
}

class Call(val scope: Scope,
           val op: Getter,
           val args: ArgList) : Expr {
    override var type: Type? = TypeInfernal.infer(scope, this)

    override fun eval(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return op.toString() + args.toString()
    }

    operator fun plus(getter: Getter): Getter {
        return Getter(scope, this, getter.combineOriginSymWithSyms())
    }

    operator fun plus(other: Call): Call {
        return Call(scope, this + other.op, other.args)
    }
}

class Getter(val scope: Scope,
             val origin: Expr,
             val syms: List<Sym>) : Expr {
    constructor(scope: Scope, origin: Expr, vararg syms: Sym) : this(scope, origin, syms.asList())

    override var type: Type? = TypeInfernal.infer(scope, this)

    override fun eval(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return if (syms.isEmpty()) {
            origin.toString()
        } else {
            "$origin.${syms.joinToString(".")}"
        }
    }

    internal fun combineOriginSymWithSyms(): List<Sym> {
        if (origin !is Sym) {
            throw UnsupportedOperationException("Can only invoke on secondary getter!")
        }
        return mutableListOf(origin) + syms
    }
}