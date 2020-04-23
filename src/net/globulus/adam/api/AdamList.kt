package net.globulus.adam.api

import net.globulus.adam.frontend.parser.ParserLog
import net.globulus.adam.frontend.parser.TypeInfernal
import net.globulus.adam.frontend.parser.ValidationException

sealed class AdamList<E : AdamList.Prop>(val props: List<E>) : Expr(), Value {
    override var alias: Sym? = null

    abstract fun get(sym: Sym): Pair<Type?, Expr?>?

    override fun toString(): String {
        return alias?.toString() ?: props.joinToString(", ", "[", "]")
    }

    override fun toValue(args: ArgList?): Value {
        return this
    }

    override fun matches(other: Type?): Boolean {
        if (this == other) {
            return true
        }
        if (other !is AdamList<*>) {
            return false
        }
        if (props.size != other.props.size) {
            // Try varargs
            return try {
                val varargProps = props.reduceToVararg()
                val otherVarargProps = other.props.reduceToVararg()
                varargProps.matches(otherVarargProps)
            } catch (e: ValidationException) {
                ParserLog.v(e.message!!)
                false
            }
        }
        return props.matches(other.props)
    }

    interface Prop {
        val type: Type
    }
}

class StructList(var gens: GenList?, props: List<Prop>) : AdamList<StructList.Prop>(props), Type {
    override var type: Type? = this

    override fun get(sym: Sym): Pair<Type?, Expr?>? {
        return props.find { it.sym == sym }?.let { it.type to it.expr }
    }

    override fun replacing(genTable: GenTable): Type {
        return StructList(null, props.map {
            Prop(it.type.replacing(genTable), it.sym, it.expr)
        })
    }

    /**
     * The following use case: ifBranching..[T]
     * Figures out which type in [gens] does T refer to, and replaces that with T from [inferredGenTable]
     */
    fun getMergedGenTable(originalSym: Sym, inferredGenTable: GenTable): GenTable {
        val genTable = GenTable()
        gens?.props?.let {
            for (i in it.indices) {
                genTable[it[i].sym] = inferredGenTable[originalSym.gens!![i]]
            }
        }
        return genTable
    }

    class Prop(override val type: Type, val sym: Sym, val expr: Expr?) : AdamList.Prop {
        override fun toString(): String {
            val str = "$type $sym"
            return expr?.let {
                "$str $it"
            } ?: str
        }
    }
}

open class RawList(scope: Scope,
                   props: List<Prop>
) : AdamList<RawList.Prop>(props) {

    override var type: Type? = null

    init {
        props.forEach { it.scope = scope }
        type = try {
            StructList(null, props.reduceToVararg())
        } catch (e: ValidationException) {
            TypeInfernal.infer(scope, this)
        }
    }

    override fun replacing(genTable: GenTable): Type {
        return this
    }

    override fun get(sym: Sym): Pair<Type?, Expr?>? {
        return props.find { it.sym == sym }?.let { null to it.expr }
    }

    class Prop(val sym: Sym?, val expr: Expr) : AdamList.Prop {
        constructor(expr: Expr) : this(null, expr)

        internal lateinit var scope: Scope

        override val type: Type
            get() = TypeInfernal.infer(scope, expr, true)

        override fun toString(): String {
            return sym?.let {
                "$it $expr"
            } ?: expr.toString()
        }
    }
}

class ArgList(private val scope: Scope, props: List<Prop>) : RawList(scope, props) {

    constructor(scope: Scope, vararg exprs: Expr) : this(scope, exprs.map { Prop(it) })

    override fun toString(): String {
        return props.joinToString(", ", "(", ")")
    }

    operator fun plus(expr: Expr): ArgList {
        return ArgList(scope, props + Prop(expr))
    }
}

class GenList(props: List<Prop>) : AdamList<GenList.Prop>(props) {
    override var type: Type? = this

    override fun get(sym: Sym): Pair<Type?, Expr?>? {
        return props.find { it.sym == sym }?.let { it.superType to null }
    }
    override fun replacing(genTable: GenTable): Type {
        return GenList(props.map {
            Prop(it.superType?.replacing(genTable), it.sym)
        })
    }

    operator fun contains(sym: Sym) = props.find { it.sym == sym } != null

    fun asStructList() = StructList(null, props.map { prop ->
        prop.superType?.let { type ->
            StructList.Prop(type, prop.sym, null)
        } ?: throw UnsupportedOperationException("Unable to convert this gens list to struct list!")
    })

    class Prop(val superType: Type?, val sym: Sym) : AdamList.Prop {
        override val type: Type
            get() = superType!!

        override fun toString(): String {
            return superType?.let {
                "$it $sym"
            } ?: sym.toString()
        }
    }
}

fun <E : AdamList.Prop> List<E>.matches(other: List<E>): Boolean {
    if (size != other.size) {
        return false
    }
    for (i in indices) {
        if (get(i).type doesntMatch other[i].type) {
            return false
        }
    }
    return true
}

fun <E : AdamList.Prop> List<E>.reduceToVararg(): List<StructList.Prop> {
    val result = mutableListOf<StructList.Prop>()
    var i = 0
    while (i < size) {
        val type = get(i).type
        var j = i + 1
        while (j < size) {
            if (get(j).type matches type) {
                j++
            } else {
                break
            }
        }
        val newType: Type
        if (j - i > 1) { // Found at least one vararg
            newType = Vararg(type)
            i = j // Start from where new type was found
        } else {
            newType = type
            i++
        }
        result += StructList.Prop(newType, Sym.EMPTY, null)
    }
    if (result.count { it.type is Vararg } > 1) {
        throw ValidationException("Found more than one Vararg in List!")
    }
    val varargIndex = result.indexOfFirst { it.type is Vararg }
    if (varargIndex != -1 && varargIndex != result.size - 1) {
        throw ValidationException("Vararg must be last in a list!")
    }
    return result
}