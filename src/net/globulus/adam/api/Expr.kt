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
                append(line.toString())
                append("\n")
            }
            append("}")
        }.toString()
    }
}

class Call(val scope: Scope,
           val op: Getter,
           val args: ArgList) : Expr {
    override var type: Type? = null

    override fun eval(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return op.toString() + args.toString()
    }

    fun patchType(allTheWay: Boolean) = apply {
        type = TypeInfernal.infer(scope, this, allTheWay)
    }

    operator fun plus(getter: Getter): Getter {
        return Getter(this, getter.combineOriginSymWithSyms())
    }

    operator fun plus(other: Call): Call {
        return Call(scope, this + other.op, other.args)
    }
}

class Getter(val origin: Expr, val syms: List<Sym>) : Expr {
    constructor(origin: Expr, vararg syms: Sym) : this(origin, syms.asList())

    override var type: Type? = null

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

    fun patchType(scope: Scope, allTheWay: Boolean) = apply {
        type = TypeInfernal.infer(scope, this, allTheWay)
    }

    internal fun combineOriginSymWithSyms(): List<Sym> {
        if (origin !is Sym) {
            throw UnsupportedOperationException("Can only invoke on secondary getter!")
        }
        return mutableListOf(origin) + syms
    }
}