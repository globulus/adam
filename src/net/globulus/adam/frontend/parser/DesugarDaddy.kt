package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*

object DesugarDaddy {
    fun hustle(scope: Scope, exprs: List<Expr>): List<Expr> {
//        val p1 = desugarLastBlockParamPass(scope, exprs)
        val p3 = desugarUnaryPass(scope, exprs)
        val p4 = desugarBinaryPass(scope, p3)
        // TODO check if the line consists of a single call with undefinedSym boobyTrap
        return p4
    }

    private fun desugarLastBlockParamPass(scope: Scope, exprs: List<Expr>): List<Expr> {
        val len = exprs.size
        if (len == 1) {
            return exprs
        }
        val desugared = mutableListOf<Expr>()
        var i = 1
        while (i < len) {
            val e1 = exprs[i - 1]
            val e2 = exprs[i]
            val lastBlockAttempt = desugarLastBlockParam(scope, desugared.last(), e1, e2)
            if (lastBlockAttempt != null) {
                desugared += lastBlockAttempt
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

    private fun desugarLastBlockParam(scope: Scope, e0: Expr, e1: Expr, e2: Expr): Expr? {
        ParserLog.ds("Attempting last block desugar $e1 $e2")
        if (e2 is Block) {
            try {
                val e1Type = TypeInfernal.infer(scope, e2, true)
                return desugarLastBlockParamWithKnownE1Type(scope, e1, e2, e1Type)
            } catch (e: UndefinedSymException) {
                ParserLog.ds("Found a combo binary candidate: ${e.message}")
                // Combo candidate is a binary desugar on previously desugared member - if (a) { } else { }
                val e0Type = TypeInfernal.infer(scope, e0, true)
                if (e0Type !is AdamList<*>) {
                    ParserLog.ds("E0 isn't a list but $e0Type")
                    throw e
                }
//                val member = TypeInfernal.infer(scope, e0Type, )
                return null
            }
        } else {
            ParserLog.ds("E2 isn't a block but $e2, bailing")
            return null
        }
    }

    private fun desugarLastBlockParamWithKnownE1Type(scope: Scope, e1: Expr, e2: Expr, e1Type: Type): Expr? {
        return null
    }

    private fun desugarUnaryPass(scope: Scope, exprs: List<Expr>): List<Expr> {
        val len = exprs.size
        if (len == 1) {
            return exprs
        }
        val desugared = mutableListOf<Expr>()
        var i = 1
        while (i < len) {
            val e1 = exprs[i - 1]
            val e2 = exprs[i]
            val unaryAttempt = desugarUnary(scope, e1, e2)
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

    private fun desugarUnary(scope: Scope, e1: Expr, e2: Expr): Expr? {
        ParserLog.ds("Attempting unary desugar $e1 $e2")
        reduceToSym(e1)?.let { e1Sym ->
            val e2Type = TypeInfernal.infer(scope, e2, true)
            (e2Type as? AdamList<*>)?.let { list ->
                try {
                    val memberType = TypeInfernal.infer(scope, list, e1Sym, true)
                    (memberType as? Blockdef)?.let {
                        return if (it.args?.props?.isNotEmpty() == true) {
                            ParserLog.ds("Member is a non-primitive blockdef: $it, bailing")
                            null
                        } else {
                            ParserLog.ds("Found primitive member $e1 of type $memberType on $e2, returning unary desugar")
                            Call(scope,
                                Getter(e2, e1Sym).patchType(scope, true),
                                ArgList(scope, emptyList())
                            ).validate()
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
            val e1Type = TypeInfernal.infer(scope, e1, true)
            (e1Type as? AdamList<*>)?.let { list ->
                try {
                    val memberType = TypeInfernal.infer(scope, list, e2Sym)
                    return desugarBinaryOnFoundMember(scope, e1, e2Sym, memberType, e3)
                } catch (e: TypeInferno) {
                    ParserLog.ds("Member not found for $e2Sym on $list, will attempt blockdef with rec")
                    val e2Type = TypeInfernal.infer(scope, e2Sym, true)
                    (e2Type as? Blockdef)?.let { e2Blockdef ->
                        e2Blockdef.rec?.let { e2RecSym ->
                            val recType = TypeInfernal.infer(scope, e2RecSym, true)
                            return if (recType == list) {
                                desugarBinaryOnFoundMember(scope, e1, e2Sym, e2Type, e3)
                            } else {
                                ParserLog.ds("E2 rectype $recType doesn't match $list, bailing")
                                null
                            }
                        } ?: run {
                            ParserLog.ds("E2 blockdef doesn't have a rec, bailing")
                            return null
                        }
                    } ?: run {
                        ParserLog.ds("E2 isn't a blockdef but ${e2Type::class.simpleName} $e2, bailing")
                        return null
                    }
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

    private fun desugarBinaryOnFoundMember(scope: Scope, e1: Expr, e2Sym: Sym, memberType: Type, e3: Expr): Expr? {
        (memberType as? Blockdef)?.let {
            return if (it.args?.props?.size == 1) {
                val e3Type = TypeInfernal.infer(scope, e3, true)
                var firstArgType = it.args.props[0].type
                if (firstArgType is Sym) {
                    // This handles the case when the return type of a block is still stored as Num
                    // when it fact it should be identified as StructList
                    firstArgType = TypeInfernal.infer(scope, firstArgType)
                }
                if (e3Type == firstArgType) {
                    ParserLog.ds("Returning binary desugar")
                    Call(scope,
                        Getter(e1, e2Sym).apply { type = memberType },
                        ArgList(scope, listOf(RawList.Prop(null, e3)))
                    ).apply {
                        type = TypeInfernal.bottomMostType(scope, memberType)
                    }.validate()
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
    }

    private fun reduceToSym(expr: Expr): Sym? {
        return when (expr) {
            is Sym -> expr
            is Getter -> expr.origin as? Sym
            else -> null
        }
    }
}