package net.globulus.adam.api

import net.globulus.adam.frontend.parser.TypeInfernal

sealed class AdamList<E>(val props: List<E>) : Expr(), Value {
    abstract fun get(sym: Sym): Pair<Type?, Expr?>?

    override fun toString(): String {
        return props.joinToString(", ", "[", "]")
    }

    override fun toValue(args: ArgList?): Value {
        return this
    }
}

class StructList(props: List<Prop>) : AdamList<StructList.Prop>(props), Type {
    override var type: Type? = this

    override fun get(sym: Sym): Pair<Type?, Expr?>? {
        return props.find { it.sym == sym }?.let { it.type to it.expr }
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

    fun asStructList() = StructList(props.map { prop ->
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