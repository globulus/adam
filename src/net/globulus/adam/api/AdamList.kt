package net.globulus.adam.api

import net.globulus.adam.frontend.parser.TypeInfernal

sealed class AdamList<E>(val props: List<E>) : Expr(), Value {
    override var alias: Sym? = null

    abstract fun get(sym: Sym): Pair<Type?, Expr?>?

    override fun toString(): String {
        return alias?.toString() ?: props.joinToString(", ", "[", "]")
    }

    override fun toValue(args: ArgList?): Value {
        return this
    }
}

class StructList(val gens: GenList?, props: List<Prop>) : AdamList<StructList.Prop>(props), Type {
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

    class Prop(val type: Type, val sym: Sym, val expr: Expr?) {
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
    override var type: Type? = TypeInfernal.infer(scope, this)

    override fun replacing(genTable: GenTable): Type {
        return this
    }

    override fun get(sym: Sym): Pair<Type?, Expr?>? {
        return props.find { it.sym == sym }?.let { null to it.expr }
    }

    class Prop(val sym: Sym?, val expr: Expr) {
        constructor(expr: Expr) : this(null, expr)
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
        return props.find { it.sym == sym }?.let { it.type to null }
    }
    override fun replacing(genTable: GenTable): Type {
        return GenList(props.map {
            Prop(it.type?.replacing(genTable), it.sym)
        })
    }

    operator fun contains(sym: Sym) = props.find { it.sym == sym } != null

    fun asStructList() = StructList(null, props.map { prop ->
        prop.type?.let { type ->
            StructList.Prop(type, prop.sym, null)
        } ?: throw UnsupportedOperationException("Unable to convert this gens list to struct list!")
    })

    class Prop(val type: Type?, val sym: Sym) {
        override fun toString(): String {
            return type?.let {
                "$it $sym"
            } ?: sym.toString()
        }
    }
}