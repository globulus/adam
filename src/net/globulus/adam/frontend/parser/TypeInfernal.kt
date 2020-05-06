package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*

object TypeInfernal {
//    val SYM_NUM = Sym("Num")
//    val SYM_STR = Sym("Str")

    infix fun Type.matches(other: Type): Boolean {
        if (this == other) {
            return true
        }
        if (this is AdamList<*> && other is AdamList<*>) {
            return this matches other
        }
        if (this is Vararg && other is Vararg) {
            return embedded matches other.embedded
        }
        if (this is Optional && other is Optional) {
            return embedded matches other.embedded
        }
        var matchAgain = false
        var thisType = if (this is Sym) whatIsThisSymAliasFor(scope, this) else this
        if (thisType is Blockdef && thisType.isPrimitive) {
            matchAgain = true
            thisType = thisType.ret
        }
        var otherType = if (other is Sym) whatIsThisSymAliasFor(other.scope, other) else other
        if (otherType is Blockdef && otherType.isPrimitive) {
            matchAgain = true
            otherType = otherType.ret
        }
        if (thisType == otherType) {
            return true
        }
        if (matchAgain) {
            return thisType matches otherType
        }
        return false
    }

    infix fun Type.doesntMatch(other: Type) = !matches(other)

    private infix fun AdamList<*>.matches(other: AdamList<*>): Boolean {
        if (props.size != other.props.size) {
            // Try varargs
            return try {
                val varargProps = props.reduceToVararg(scope)
                val otherVarargProps = other.props.reduceToVararg(other.scope)
                varargProps.matches(otherVarargProps)
            } catch (e: ValidationException) {
                ParserLog.v(e.message!!)
                false
            }
        }
        return props.matches(other.props)
    }

    internal fun infer(scope: Scope, expr: Expr): Type {
//        if (allTheWay) {
//            if (expr.type is Sym) {
//                return infer(scope, expr.type as Sym, true)
//            } else {
//                ((expr.type as? Blockdef)?.ret?.let { ret ->
//                    (ret as? Sym)?.let {
//                        return infer(scope, it, true)
//                    }
//                    (ret as? StructList)?.let {
//                        return it
//                    }
//                })
//            }
//        }
        return when (expr) {
            is Num -> infer(scope, Sym(scope, "Num"))
            is Str -> infer(scope, Sym(scope, "Str"))
            is Sym -> whatIsThisSymAliasFor(scope, expr)
            is RawList -> expr.type
            is Block -> infer(scope, expr)
            is Call -> expr.type //infer(scope, expr)
            is Getter -> infer(scope, expr)
            else -> throw TypeInferno("Invalid expr $expr")
        }
    }

    fun whatIsThisSymAliasFor(scope: Scope, sym: Sym, throwExceptionIfNotFound: Boolean = false): Type {
        var currentScope: Scope? = scope
        do {
            currentScope?.typeAliases?.get(sym)?.let {
                return if (it is Sym) {
                    whatIsThisSymAliasFor(scope, it)//.apply { alias = it }
                } else {
                    it
                }
            }
            currentScope = currentScope?.parent
        } while (currentScope != null)
        if (throwExceptionIfNotFound) {
            throw UndefinedSymException(sym)
        } else {
            return sym
        }
//        if (allTheWay) {
//            return sym
//        } else {
//            throw UndefinedSymException(sym)
//        }
    }

    fun tryToInferList(scope: Scope, expr: Expr): AdamList<*>? {
        return inferUntilType(scope, expr, AdamList::class.java)
    }

    fun tryToInferBlockdef(scope: Scope, expr: Expr): Blockdef? {
        return inferUntilType(scope, expr, Blockdef::class.java)
    }

    fun tryToInferBlockdefFromList(scope: Scope, list: AdamList<*>, sym: Sym): Blockdef? {
        return tryFromListAs(scope, list, sym, Blockdef::class.java)
    }

    fun tryToInferListFromList(scope: Scope, list: AdamList<*>, sym: Sym): AdamList<*>? {
        return tryFromListAs(scope, list, sym, AdamList::class.java)
    }

    fun <T: Type> tryFromListAs(scope: Scope, list: AdamList<*>, sym: Sym, tclazz: Class<T>): T? {
        return when (val item = getFromList(list, sym)) {
            is Type -> {
                inferUntilType(scope, item, tclazz)
            }
            is Expr -> {
                inferUntilType(scope, item, tclazz)
            }
            else -> {
                null
            }
        }
    }

    private fun <T: Type> inferUntilType(scope: Scope, expr: Expr, tclazz: Class<T>): T? {
        if (tclazz.isAssignableFrom(expr::class.java)) {
            return expr as T
        }
        val oneLevelUp = when (expr) {
            is Num -> whatIsThisSymAliasFor(scope, Sym(scope, "Num"))
            is Str -> whatIsThisSymAliasFor(scope, Sym(scope, "Str"))
            is Sym -> whatIsThisSymAliasFor(scope, expr, true)
            is Block -> expr.ret
            is Call -> expr.type
            is Getter -> expr.type
            else -> return null
        }
        return if (oneLevelUp is Expr) {
            inferUntilType(scope, oneLevelUp as Expr, tclazz)
        } else if (tclazz.isAssignableFrom(oneLevelUp::class.java)) {
            oneLevelUp as T
        } else {
            null
        }
    }

    private fun <T: Type> inferUntilType(scope: Scope, type: Type, tclazz: Class<T>): T? {
        if (tclazz.isAssignableFrom(type::class.java)) {
            return type as T
        }
        val oneLevelUp = when (type) {
            is Num -> whatIsThisSymAliasFor(scope, Sym(scope, "Num"))
            is Str -> whatIsThisSymAliasFor(scope, Sym(scope, "Str"))
            is Sym -> whatIsThisSymAliasFor(scope, type)
            is Blockdef -> {
                if (type.isPrimitive) {
                    type.ret
                } else {
                    null
                }
            }
            else -> return null
        }
        return if (oneLevelUp is Type) {
            inferUntilType(scope, oneLevelUp, tclazz)
        } else {
            null
        }
    }

    fun infer(scope: Scope, list: RawList): StructList {
        return StructList(scope,null, list.props.map {
            StructList.Prop(try {
                infer(scope, it.expr)
            } catch (e: UndefinedSymException) {
                e.sym
            },it.sym ?: Sym.empty(scope), it.expr)
        })
    }

    private fun infer(scope: Scope, block: Block): Type {
        return infer(scope, block.body)
    }

    internal fun infer(scope: Scope, body: List<Expr>): Type {
        return body.lastOrNull()?.let {
            infer(scope, it)
        } ?: throw TypeInferno("Empty blocks don't have return types")
    }

    private fun infer(scope: Scope, call: Call): Type {
        // For chained calls like a.=(3).+(4), the getter op's type has already been
        // inferred via allTheWay = true (because otherwise it'd be a Blockdef). Invoking
        // infer(scope, call.op, allTheWay = false) would take it back to being a Blockdef,
        // which is wrong.
//        if (call.op.type != null && call.op.type !is Blockdef && !allTheWay) {
//            return call.op.type!!
//        }
        return infer(scope, call.op)
    }

    private fun infer(scope: Scope, getter: Getter): Type {
        var prevType = infer(scope, getter.origin)
        for (sym in getter.syms) {
            prevType = (prevType as? AdamList<*>)?.let {
                infer(scope, it, sym)
            } ?: throw TypeInferno("Getter sym must be a list, instead it's $prevType")
        }
        return prevType
    }

    private fun getFromList(list: AdamList<*>, sym: Sym): Any {
        return list.get(sym)?.let { pair ->
            pair.first?.let {
                it
            } ?: pair.second?.let {
                it
            } ?: throw TypeInferno("Both type and expr are null, this should never happen!")
        } ?: throw TypeInferno("Unable to find sym $sym in $list")
    }

    fun getItemType(list: AdamList<*>, sym: Sym): Type {
        return when (val item = getFromList(list, sym)) {
            is Type -> item
            is Expr -> item.type
            else -> throw IllegalStateException("We shouldnt end up here")
        }
    }

    private fun infer(scope: Scope, list: AdamList<*>, sym: Sym): Type {
        return list.get(sym)?.let { pair ->
            pair.first?.let {
//                if (allTheWay && it is Sym) {
//                    infer(scope, it, true)
//                } else {
                    it
//                }
            } ?: pair.second?.let {
                infer(scope, it)
            } ?: throw TypeInferno("Both type and expr are null, this should never happen!")
        } ?: throw TypeInferno("Unable to find sym $sym in $list")
    }

//    fun bottomMostType(scope: Scope, type: Type): Type {
//        return when (type) {
//            is Sym -> infer(scope, type)
//            is Blockdef -> bottomMostType(scope, type.ret)
//            else -> type
//        }
//    }
}

open class TypeInferno(message: String) : Exception("Unable to infer type: $message")

class UndefinedSymException(val sym: Sym) : TypeInferno("Undefined sym $sym")