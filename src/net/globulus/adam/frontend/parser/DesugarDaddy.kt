package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*

object DesugarDaddy {
    fun hustle(scope: Scope, exprs: List<Expr>): List<Expr> {
//        val p1 = desugarLastBlockParamPass(scope, exprs)
        val p2 = desugarUnaryPass(scope, exprs)// p1)
//        val p3 = desugarBinaryPass(scope, p2)
        return p2 // p3
    }

    private fun desugarUnaryPass(scope: Scope, exprs: List<Expr>) = desugarTwoOpPass(scope, exprs) { s, _, e1, e2 ->
        ParserLog.ds("Attempting unary desugar $e1 $e2")
        val e1Type = e1.type!!
        if (e1Type is Sym) {
            val e2Type = e2.type!!
            (e2Type as? AdamList<*>)?.let { list ->
                try {
                    val memberType = TypeInfernal.infer(scope, list, e1Type)
                    (memberType as? Blockdef)?.let {
                        if (it.args?.props?.isNotEmpty() == true) {
                            ParserLog.ds("Found primitive member $e1 of type $memberType on $e2, returning unary desugar")
                            Call(scope, Getter(scope, e2, e1Type), ArgList(scope, emptyList()))
                        } else {
                            ParserLog.ds("Member is a non-primitive blockdef: $it, bailing")
                            null
                        }
                    } ?: run {
                        ParserLog.ds("Member is not a Blockdef: $memberType, bailing")
                        null
                    }
                } catch (e: TypeInferno) {
                    ParserLog.ds("Member not found for $e1, bailing")
                    null
                }
            } ?: run {
                ParserLog.ds("E2 isn't a list but ${e2Type::class.simpleName} $e2Type, bailing")
                null
            }
        } else {
            ParserLog.ds("E1 isn't a symbol but ${e1::class.simpleName} $e1, bailing")
            null
        }
    }

    private fun desugarTwoOpPass(
        scope: Scope,
        exprs: List<Expr>,
        attempt: (Scope, List<Expr>?, Expr, Expr) -> Expr?
    ): List<Expr> {
        val len = exprs.size
        if (len == 1) {
            return exprs
        }
        val desugared = mutableListOf<Expr>()
        var i = 1
        while (i < len) {
            val e1 = exprs[i - 1]
            val e2 = exprs[i]
            val unaryAttempt = attempt(scope, desugared, e1, e2)
            if (unaryAttempt != null) {
                desugared += unaryAttempt
                i++
            } else {
                desugared += e1
            }
            i++
            if (i == len) {
                desugared += e2
            }
        }
        return desugared
    }
}