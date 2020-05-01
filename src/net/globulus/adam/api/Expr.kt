package net.globulus.adam.api

import net.globulus.adam.frontend.parser.GenericsException
import net.globulus.adam.frontend.parser.ParserLog
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
    var genTable: GenTable? = null

    override fun toValue(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return op.toString() + args.toString()
    }

    fun validate() = apply {
        patchType(false)
        (type as? Blockdef)?.let {
            var blockdef = it
            if (args.props.size != blockdef.args?.props?.size ?: 0) {
                throw ValidationException("Args arities don't match!")
            }
            if (blockdef.gens != null) {
                inferGens(it, blockdef.gens!!)
                blockdef = type as Blockdef // Reassign as type likely changed after gens were inferred
            }
            for (i in args.props.indices) {
                // TODO validate by Syms
                if (TypeInfernal.infer(scope, args.props[i].expr, true) doesntMatch
                        TypeInfernal.bottomMostType(scope, blockdef.args!!.props[i].type)) {
                    throw ValidationException("Arg types don't match at index $i!")
                }
            }
        } ?: throw ValidationException("Call type isn't a Blockdef but ${type!!::class.simpleName}!")
    }

    fun inferGens(blockdef: Blockdef, gens: GenList) {
        if (genTable == null) {
            genTable = (op.origin as? Call)?.genTable ?: GenTable()
        }
        val props = mutableListOf<StructList.Prop>()
        for (i in args.props.indices) {
            val blockdefArg = blockdef.args!!.props[i]
            // TODO validate by Syms
            val suppliedArgType = TypeInfernal.infer(scope, args.props[i].expr, true)
            val expectedArgType = TypeInfernal.bottomMostType(scope, blockdefArg.type)
            val inferredType = if (suppliedArgType matches expectedArgType) {
                expectedArgType
            } else {
                val suppliedReifiedArgType = reifyGenType(gens, suppliedArgType, null)
                val expectedReifiedArgType = reifyGenType(gens, expectedArgType, suppliedReifiedArgType)
                if (expectedReifiedArgType doesntMatch suppliedReifiedArgType) {
                    throw ValidationException("Arg types don't match at index $i!")
                }
                expectedReifiedArgType
            }

            props += StructList.Prop(inferredType, blockdefArg.sym, null)
        }
        val reifiedRet = reifyGenType(gens, blockdef.ret, null)
        type = Blockdef(null, blockdef.rec, StructList(null, props), reifiedRet)
    }

    fun reifyGenType(blockdefGens: GenList,
                     checkedType: Type,
                     controlType: Type?): Type {
        if (checkedType is Sym) {
            if (checkedType.gens != null) {
                // If it has gens, it implies that it's a StructList underneath
                val structList = TypeInfernal.infer(scope, checkedType, true) as StructList
                val mergedGenTable = structList.getMergedGenTable(checkedType, genTable!!)
                val replacedList = structList.replacing(mergedGenTable).apply {
                    alias = Sym(checkedType.value).apply {
                        gens = checkedType.gens!!.map { Sym(genTable!![it].toString()) }
                    }
                }
                return replacedList
            }
            if (checkedType in blockdefGens) {
                if (checkedType in genTable!!) {
                    val genTableType = genTable!![checkedType]
                    if (controlType != null && genTableType doesntMatch controlType) {
                        throw GenericsException("Generics type $checkedType is already defined as $genTableType, while the supplied arg is $controlType!")
                    } else {
                        return genTableType
                    }
                } else if (controlType != null) {
                    genTable!![checkedType] = controlType
                    ParserLog.v("Inferred generic type of $controlType for $checkedType.")
                    return controlType
                }
            }
        } else if (checkedType is StructList && controlType is StructList) {
            val props = mutableListOf<StructList.Prop>()
            for (i in checkedType.props.indices) {
                val prop = checkedType.props[i]
                val reifiedType = reifyGenType(blockdefGens, prop.type, controlType.props[i].type)
                props += StructList.Prop(reifiedType, prop.sym, prop.expr)
            }
            return StructList(checkedType.gens, props)
        } else if (checkedType is Vararg && controlType is Vararg) {
            return Vararg(reifyGenType(blockdefGens, checkedType.embedded, controlType.embedded))
        } else if (checkedType is Optional && controlType is Optional) {
            return Optional(reifyGenType(blockdefGens, checkedType.embedded, controlType.embedded))
        }
        return checkedType
    }

    fun patchType(allTheWay: Boolean) = apply {
        type = TypeInfernal.infer(scope, this, allTheWay)
    }

    operator fun plus(getter: Getter): Getter {
        return Getter(this, getter.combineOriginSymWithSyms()).patchType(scope, true)
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
    val unpacked: Expr get() = if (isPrimitive) origin else this

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