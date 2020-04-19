package net.globulus.adam.api

import net.globulus.adam.frontend.parser.TypeInfernal
import net.globulus.adam.frontend.parser.ValidationException

abstract class Expr {
    abstract var type: Type?
    protected abstract fun toValue(args: ArgList? = null): Value
    private val boobyTraps = mutableListOf<BoobyTrap>()

    internal fun placeTrap(boobyTrap: BoobyTrap) {
        boobyTraps += boobyTrap
    }

    internal fun clearTraps() {
        boobyTraps.clear()
    }

    fun eval(scope: Scope, args: ArgList? = null): Value {
        for (trap in boobyTraps) {
            if (!trap.defusal.defuse(scope)) {
                throw trap.exception
            }
        }
        return toValue(args)
    }
}

class Block(val args: StructList?,
            val ret: Type,
            val body: List<Expr>) : Expr() {
    override var type: Type? = ret

    override fun toValue(args: ArgList?): Value {
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
           val args: ArgList) : Expr() {
    override var type: Type? = null

    override fun toValue(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return op.toString() + args.toString()
    }

    fun validate() = apply {
        patchType(false)
        (type as? Blockdef)?.let {
            if (args.props.size != it.args?.props?.size ?: 0) {
                throw ValidationException("Args arities don't match!")
            }
            for (i in args.props.indices) {
                // TODO validate by Syms
                if (TypeInfernal.infer(scope, args.props[i].expr, true) != TypeInfernal.bottomMostType(scope, it.args!!.props[i].type)) {
                    throw ValidationException("Arg types don't match at index $i!")
                }
            }
        } ?: throw ValidationException("Call type isn't a Blockdef!")
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

class Getter(val origin: Expr, val syms: List<Sym>) : Expr() {
    constructor(origin: Expr, vararg syms: Sym) : this(origin, syms.asList())

    override var type: Type? = null

    override fun toValue(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return if (syms.isEmpty()) {
            origin.toString()
        } else {
            "$origin.${syms.joinToString(".")}"
        }
    }

    val isPrimitive = syms.isEmpty()

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