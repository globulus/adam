package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*
import kotlin.reflect.KFunction

object DesugarDaddy {
    fun hustle(scope: Scope, exprs: List<Expr>): List<Expr> {
//        val p1 = desugarLastBlockParamPass(scope, exprs)
        val p2 = desugarUnaryPass(scope, exprs)// p1)
        val p3 = desugarBinaryPass(scope, p2)
        return p3
    }

    private fun desugarUnaryPass(scope: Scope, exprs: List<Expr>) = desugarTwoOpPass(scope, exprs, DesugarDaddy::desugarUnary)

    private fun desugarTwoOpPass(
        scope: Scope,
        exprs: List<Expr>,
        attempt: KFunction<Expr?>
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
            val unaryAttempt = attempt.call(scope, desugared, e1, e2)
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

    fun desugarUnary(scope: Scope, unused: List<Expr>, e1: Expr, e2: Expr): Expr? {
        ParserLog.ds("Attempting unary desugar $e1 $e2")
        reduceToSym(e1)?.let { e1Sym ->
            val e2Type = e2.type!!
            (e2Type as? AdamList<*>)?.let { list ->
                try {
                    val memberType = TypeInfernal.infer(scope, list, e1Sym)
                    (memberType as? Blockdef)?.let {
                        return if (it.args?.props?.isNotEmpty() == true) {
                            ParserLog.ds("Member is a non-primitive blockdef: $it, bailing")
                            null
                        } else {
                            ParserLog.ds("Found primitive member $e1 of type $memberType on $e2, returning unary desugar")
                            Call(scope, Getter(scope, e2, e1Sym), ArgList(scope, emptyList()))
                        }
                    } ?: run {
                        ParserLog.ds("Member is not a Blockdef: $memberType, bailing")
                        return null
                    }
                } catch (e: TypeInferno) {
                    ParserLog.ds("Member not found for $e1, bailing")
                    return null
                }
            } ?: run {
                ParserLog.ds("E2 isn't a list but ${e2Type::class.simpleName} $e2Type, bailing")
                return null
            }
        } ?: run {
            ParserLog.ds("E1 isn't a Sym but ${e1::class.simpleName} $e1, bailing")
            return null
        }
    }

    private fun desugarBinaryPass(scope: Scope, exprs: List<Expr>): List<Expr> {
        val len = exprs.size
        if (len < 3) { // Binary desugar candidate has at least 3 members
            return exprs
        }
        val desugared = mutableListOf<Expr>()
        var i = 2
        while (i < len) {
            val e1 = exprs[i - 2]
            val e2 = exprs[i - 1]
            val e3 = exprs[i]
            val binaryAttempt = desugarBinary(scope, e1, e2, e3)
            if (binaryAttempt != null) {
                desugared += binaryAttempt
                i += 2
            } else {
                desugared += e1
            }
            i++
            if (i == len) {
                desugared += e2
                desugared += e3
            }
        }
        return desugared
    }

    private fun desugarBinary(scope: Scope, e1: Expr, e2: Expr, e3: Expr): Expr? {
        ParserLog.ds("Attempting binary desugar $e1 $e2 $e3")
        reduceToSym(e2)?.let { e2Sym ->
            val e1Type = e1.type!!
            (e1Type as? AdamList<*>)?.let { list ->
                try {
                    val memberType = TypeInfernal.infer(scope, list, e2Sym)
                    (memberType as? Blockdef)?.let {
                        return if (it.args?.props?.size == 1) {
                            val e3Type = e3.type!!
                            var firstArgType = it.args.props[0].type
                            if (firstArgType is Sym) {
                                // This handles the case when the return type of a block is still stored as Num
                                // when it fact it should be identified as StructList
                                firstArgType = TypeInfernal.infer(scope, firstArgType)
                            }
                            if (e3Type == firstArgType) {
                                ParserLog.ds("Returning binary desugar")
                                Call(scope, Getter(scope, e1, e2Sym), ArgList(scope, listOf(RawList.Prop(null, e3))))
                            } else {
                                ParserLog.ds("E3 type ${e3Type::class.simpleName} $e3Type doesn't match first arg type ${firstArgType::class.simpleName} $firstArgType")
                                null
                            }
                        } else {
                            ParserLog.ds("Member is not a single-arg blockdef: $it, bailing")
                            null
                        }
                    } ?: run {
                        ParserLog.ds("Member is not a Blockdef: $memberType, bailing")
                        return null
                    }
                } catch (e: TypeInferno) {
                    ParserLog.ds("Member not found for $e2, bailing")
                    return null
                }
            } ?: run {
                ParserLog.ds("E1 isn't a list but ${e1Type::class.simpleName} $e1Type, bailing")
                return null
            }
        } ?: run {
            ParserLog.ds("E2 isn't a symbol but ${e2::class.simpleName} $e2")
            return null
        }
    }

    private fun reduceToSym(expr: Expr): Sym? {
        return when (expr) {
            is Sym -> expr
            is Getter -> expr.origin as? Sym
            else -> null
        }
    }
}