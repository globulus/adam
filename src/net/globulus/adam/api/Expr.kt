package net.globulus.adam.api

import net.globulus.adam.frontend.parser.GenericsException
import net.globulus.adam.frontend.parser.ParserLog
import net.globulus.adam.frontend.parser.TypeInfernal
import net.globulus.adam.frontend.parser.TypeInfernal.doesntMatch
import net.globulus.adam.frontend.parser.TypeInfernal.matches
import net.globulus.adam.frontend.parser.ValidationException

abstract class Expr {
    abstract var type: Type
    protected abstract fun toValue(args: ArgList? = null): Value

    fun eval(scope: Scope, args: ArgList? = null): Value {
        return toValue(args)
    }
}

class Block(val bodyScope: Scope,
            val args: StructList?,
            val ret: Type,
            val body: List<Expr>) : Expr() {
    override var type: Type = ret

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

    override lateinit var type: Type
    var genTable: GenTable? = null

    override fun toValue(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return op.toString() + args.toString()
    }

    fun validate() = apply {
        val getterType = TypeInfernal.tryToInferBlockdef(scope, op)
        getterType?.let {
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
                val providedType = TypeInfernal.infer(scope, args.props[i].expr)
                val expectedType = blockdef.args!!.props[i].type
                if (providedType doesntMatch expectedType) {
                    throw ValidationException("Arg types don't match at index $i: $providedType vs $expectedType!")
                }
            }
            type = blockdef.ret
        } ?: throw ValidationException("Call type isn't a Blockdef but $op!")
    }

    fun inferGens(blockdef: Blockdef, gens: GenList) {
        if (genTable == null) {
            genTable = (op.origin as? Call)?.genTable ?: GenTable()
        }
        val props = mutableListOf<StructList.Prop>()
        for (i in args.props.indices) {
            val blockdefArg = blockdef.args!!.props[i]
            // TODO validate by Syms
            val suppliedArgType = TypeInfernal.infer(scope, args.props[i].expr)
            val expectedArgType = blockdefArg.type
//            val suppliedArgType = TypeInfernal.infer(scope, args.props[i].expr, true)
//            val expectedArgType = TypeInfernal.bottomMostType(scope, blockdefArg.type)
            val inferredType = if (suppliedArgType matches expectedArgType) {
                expectedArgType
            } else {
                val suppliedReifiedArgType = reifyGenType(gens, suppliedArgType, null)
                val expectedReifiedArgType = reifyGenType(gens, expectedArgType, suppliedReifiedArgType)
                if (expectedReifiedArgType doesntMatch suppliedReifiedArgType) {
                    throw ValidationException("Arg types don't match at index $i: $suppliedReifiedArgType vs $expectedReifiedArgType!!")
                }
                expectedReifiedArgType
            }

            props += StructList.Prop(inferredType, blockdefArg.sym, null)
        }
        val reifiedRet = reifyGenType(gens, blockdef.ret, null)
        type = Blockdef(scope, null, blockdef.rec, StructList(scope,null, props), reifiedRet)
    }

    fun reifyGenType(blockdefGens: GenList,
                     checkedType: Type,
                     controlType: Type?): Type {
        if (checkedType is Sym) {
            if (checkedType.gens != null) {
                // If it has gens, it implies that it's a StructList underneath
                val structList = TypeInfernal.tryToInferList(scope, checkedType) as StructList // TypeInfernal.infer(scope, checkedType, true) as StructList
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
        } else if (checkedType is Blockdef) {
            return reifyGenType(blockdefGens, checkedType.ret, controlType)
        } else if (checkedType is StructList && controlType is StructList) {
            val props = mutableListOf<StructList.Prop>()
            for (i in checkedType.props.indices) {
                val prop = checkedType.props[i]
                val reifiedType = reifyGenType(blockdefGens, prop.type, controlType.props[i].type)
                props += StructList.Prop(reifiedType, prop.sym, prop.expr)
            }
            return StructList(checkedType.scope, checkedType.gens, props)
        } else if (checkedType is Vararg && controlType is Vararg) {
            return Vararg(reifyGenType(blockdefGens, checkedType.embedded, controlType.embedded))
        } else if (checkedType is Optional && controlType is Optional) {
            return Optional(reifyGenType(blockdefGens, checkedType.embedded, controlType.embedded))
        }
        return checkedType
    }

    operator fun plus(getter: Getter): Getter {
        return Getter(this, getter.combineOriginSymWithSyms()).patchType(scope)
    }

    operator fun plus(other: Call): Call {
        return Call(scope, this + other.op, other.args)
    }
}

class Getter(val origin: Expr, val syms: List<Sym>) : Expr() {
    constructor(origin: Expr, vararg syms: Sym) : this(origin, syms.asList())

    override lateinit var type: Type

    val isPrimitive = syms.isEmpty()
    val unpacked: Expr get() = if (isPrimitive) origin else this

    fun patchType(scope: Scope): Getter {
        type = if (isPrimitive) {
            origin.type
        } else {
            TypeInfernal.tryToInferList(scope, origin)?.let {
                var prevType: AdamList<*>? = it
                for (i in 0 until syms.size - 1) {
                    val sym = syms[i]
                    val list = prevType!!
                    prevType = TypeInfernal.tryToInferListFromList(scope, list, syms[i])
                    if (prevType == null) {
                        throw ValidationException("Invalid getter chain, unable to get element $sym in $list")
                    }
                }
                val finalTerm = TypeInfernal.getItemType(prevType!!, syms.last())
                finalTerm
            } ?: throw ValidationException("Invalid getter, origin isn't a list but $origin")
        }
        return this
    }

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

    internal fun combineOriginSymWithSyms(): List<Sym> {
        if (origin !is Sym) {
            throw UnsupportedOperationException("Can only invoke on secondary getter!")
        }
        return mutableListOf(origin) + syms
    }
}