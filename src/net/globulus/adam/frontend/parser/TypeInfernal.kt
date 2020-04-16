package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*

object TypeInfernal {
    private val SYM_NUM = Sym("Num")
    private val SYM_STR = Sym("Str")

    fun infer(scope: Scope, expr: Expr, allTheWay: Boolean = false): Type {
        if (allTheWay) {
            if (expr.type is Sym) {
                return infer(scope, expr.type as Sym, true)
            } else {
                ((expr.type as? Blockdef)?.ret as? Sym)?.let {
                    return infer(scope, it, true)
                }
            }
        }
        return expr.type?.let { it }
            ?: when (expr) {
                is Num -> infer(scope, SYM_NUM, allTheWay)
                is Str -> infer(scope, SYM_STR, allTheWay)
                is Sym -> infer(scope, expr, allTheWay)
                is RawList -> infer(scope, expr, allTheWay)
                is Block -> infer(scope, expr, allTheWay)
                is Call -> infer(scope, expr, allTheWay)
                is Getter -> infer(scope, expr, allTheWay)
                else -> throw TypeInferno("Invalid expr $expr")
            }
    }

    fun infer(scope: Scope, sym: Sym, allTheWay: Boolean = false): Type {
        var currentScope: Scope? = scope
        do {
            currentScope?.typeAliases?.get(sym)?.let {
                return if (allTheWay && it is Sym) {
                    infer(scope, it, allTheWay)
                } else {
                    it
                }
            }
            currentScope = currentScope?.parent
        } while (currentScope != null)
        if (allTheWay) {
            return sym
        } else {
            throw TypeInferno("Undefined sym $sym")
        }
    }

    fun infer(scope: Scope, list: RawList, allTheWay: Boolean = false): StructList {
        return StructList(list.props.map {
            StructList.Prop(infer(scope, it.expr, allTheWay), it.sym ?: Sym.EMPTY, it.expr)
        })
    }

    fun infer(scope: Scope, block: Block, allTheWay: Boolean = false): Type {
        return infer(scope, block.body, allTheWay)
    }

    fun infer(scope: Scope, body: List<Expr>, allTheWay: Boolean = false): Type {
        return body.lastOrNull()?.let {
            infer(scope, it, allTheWay)
        } ?: throw TypeInferno("Empty blocks don't have return types")
    }

    fun infer(scope: Scope, call: Call, allTheWay: Boolean = false): Type {
        return infer(scope, call.op, allTheWay)
    }

    fun infer(scope: Scope, getter: Getter, allTheWay: Boolean = false): Type {
        var prevType = infer(scope, getter.origin, allTheWay)
        for (sym in getter.syms) {
            prevType = (prevType as? AdamList<*>)?.let {
                infer(scope, it, sym, allTheWay)
            } ?: throw TypeInferno("Getter sym must be a list, instead it's $prevType")
        }
        return prevType
    }

    fun infer(scope: Scope, list: AdamList<*>, sym: Sym, allTheWay: Boolean = false): Type {
        return list.get(sym)?.let { pair ->
            pair.first?.let {
                if (allTheWay && it is Sym) {
                    infer(scope, it, true)
                } else {
                    it
                }
            } ?: pair.second?.let {
                infer(scope, it, allTheWay)
            } ?: throw TypeInferno("Both type and expr are null, this should never happen!")
        } ?: throw TypeInferno("Unable to find sym $sym in $list")
    }
}

class TypeInferno(message: String) : Exception("Unable to infer type: $message")