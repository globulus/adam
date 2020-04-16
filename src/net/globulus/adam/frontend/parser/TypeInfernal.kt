package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*

object TypeInfernal {
    private val SYM_NUM = Sym("Num")
    private val SYM_STR = Sym("Str")

    fun infer(scope: Scope, expr: Expr): Type {
        return expr.type?.let { it }
            ?: when (expr) {
                is Num -> infer(scope, SYM_NUM)
                is Str -> infer(scope, SYM_STR)
                is Sym -> infer(scope, expr)
                is RawList -> infer(scope, expr)
                is Block -> infer(scope, expr)
                is Call -> infer(scope, expr)
                is Getter -> infer(scope, expr)
                else -> throw TypeInferno("Invalid expr $expr")
            }
    }

    fun infer(scope: Scope, sym: Sym): Type {
        var currentScope: Scope? = scope
        do {
            currentScope?.typeAliases?.get(sym)?.let {
                return if (it is Sym) {
                    infer(scope, it)
                } else {
                    it
                }
            }
            currentScope = currentScope?.parent
        } while (currentScope != null)
        throw TypeInferno("Undefined sym $sym")
    }

    fun infer(scope: Scope, list: RawList): StructList {
        return StructList(list.props.map {
            StructList.Prop(infer(scope, it.expr), it.sym ?: Sym.EMPTY, it.expr)
        })
    }

    fun infer(scope: Scope, block: Block): Type {
        return infer(scope, block.body)
    }

    fun infer(scope: Scope, body: List<Expr>): Type {
        return body.lastOrNull()?.let {
            infer(scope, it)
        } ?: throw TypeInferno("Empty blocks don't have return types")
    }

    fun infer(scope: Scope, call: Call): Type {
        return infer(scope, call.op)
    }

    fun infer(scope: Scope, getter: Getter): Type {
        var prevType = getter.origin.type!!
        for (sym in getter.syms) {
            prevType = (prevType as? AdamList<*>)?.let {
                infer(scope, it, sym)
            } ?: throw TypeInferno("Getter sym must be a list, instead it's $prevType")
        }
        return prevType
    }

    fun infer(scope: Scope, list: AdamList<*>, sym: Sym): Type {
        return list.get(sym)?.let { pair ->
            pair.first?.let {
                it
            } ?: pair.second?.let {
                infer(scope, it)
            } ?: throw TypeInferno("Both type and expr are null, this should never happen!")
        } ?: throw TypeInferno("Unable to find sym $sym in $list")
    }
}

class TypeInferno(message: String) : Exception("Unable to infer type: $message")