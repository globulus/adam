package net.globulus.adam.frontend.parser

import net.globulus.adam.api.*

object DesugarDaddy {
    fun hustle(scope: Scope, exprs: List<Expr>): List<Expr> {
        val p1 = exhaust(exprs) { desugarLastBlockParamPass(scope, it) }
        val p2 = exhaust(p1) { desugarUnaryPass(scope, it) }
        val p3 = exhaust(p2) { desugarBinaryPass(scope, it) }
        // TODO check if the line consists of a single call with undefinedSym boobyTrap
        return p3
    }

    /**
     * Reruns the desugaring block until it's exhausted, i.e until it didn't produce
     * any new desugars. This is imperative for situations such as:
     * if (a) { 1 } else if (b) { 2 } else if (c) { 3 } else { 4 }
     */
    fun exhaust(exprs: List<Expr>, block: (List<Expr>) -> List<Expr>): List<Expr> {
        var desugared = exprs
        var oldSize = desugared.size
        while (true) {
            desugared = block(desugared)
            if (desugared.size == oldSize) {
                break
            } else {
                oldSize = desugared.size
            }
        }
        return desugared
    }

    /**
     * Definitions:
     *  a) Proper prior element is a a Sym that evaluates to a Blockdef with adequate arity and types match.
     * The algorithm is as follows:
     *  1. Any free-standing [Block] is an anomaly unless it's the only thing on the line.
     *  2. Check the Expr before:
     *      2.1 If it's single-arg proper prior, desugar.
     *      2.2 If it's an ArgList:
     *          2.2.1 Add it to it as the last element.
     *          2.2.2 Check the element before the ArgList to be proper prior. If it is, desugar.
     *          2.2.3 Otherwise, check if e0 is Sym. If it is, try combo desugar with e0.e1(e2). If it succeeds, desugar.
     *          2.2.4 Otherwise, raise error.
     *      2.3. If it's any other Expr, construct an artificial ArgList of that element and the block. Proceed from step 2.2.2.
     *      2.4 If everything else fails, try the 4 piece combo - e(-1).e(0)(combinedArgList).
     *      2.5 Otherwise, raise an error.
     */
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
            if (e2 is Block) {
                val attemptWithE1 = checkForProperPrior(scope, e1, e2)
                if (attemptWithE1 != null) {
                    ParserLog.ds("Found last block desugar with E1 and E2: $attemptWithE1")
                    desugared += attemptWithE1
                    i++
                } else if (i > 1) {
                    val e0 = desugared.last()
                    var attemptWithE0: Expr? = null
                    if (e1 is Sym) {
                        attemptWithE0 = try {
                            val e1Type = TypeInfernal.infer(scope, e1, true)
                            if (e1Type is Sym) {
                                attemptThreePieceCombo(scope, e0, e1, e2)
                            } else {
                                null
                            }
                        } catch (e: UndefinedSymException) {
                            attemptThreePieceCombo(scope, e0, e1, e2)
                        }
                    }
                    val argList = if (e1 is ArgList) {
                        e1 + e2
                    } else {
                        ArgList(scope, e1, e2)
                    }
                    if (attemptWithE0 == null) {
                        attemptWithE0 = checkForProperPrior(scope, e0, argList)
                    }
                    if (attemptWithE0 != null) {
                        ParserLog.ds("Found last block desugar with E0, E1 and E2: $attemptWithE0")
                        desugared.removeLast()
                        desugared += attemptWithE0
                        i++
                    } else if (i > 2 && e0 is Sym) {
                        ParserLog.ds("Checking 4 piece combo")
                        // One last thing to try - the 4 piece combo
                        val eMinus1 = desugared[desugared.size - 2]
                        val attemptWithEMinus1 = try {
                            checkForProperPrior(
                                scope,
                                Getter(eMinus1, e0).patchType(scope, true),
                                argList
                            )
                        } catch (e: TypeInferno) {
                            null
                        }
                        if (attemptWithEMinus1 != null) {
                            ParserLog.ds("Found last block desugar with E-1, E0, E1 and E2: $attemptWithEMinus1")
                            desugared.removeLast()
                            desugared.removeLast()
                            desugared += attemptWithEMinus1
                            i++
                        } else {
                            throw ValidationException("Standalone block detected, unable to tie it to any calls!")
                        }
                    } else {
                        throw ValidationException("Standalone block detected, unable to tie it to any calls!")
                    }
                }
            } else {
                ParserLog.ds("E2 isn't a block but ${e2::class.simpleName}, bailing")
                desugared += e1
            }
            i++
            if (i == len) {
                desugared += e2
            }
        }
        return desugared
    }

    private fun checkForProperPrior(scope: Scope, e1: Expr, vararg args: Expr) = checkForProperPrior(scope, e1, ArgList(scope, *args))

    private fun checkForProperPrior(scope: Scope, e1: Expr, argList: ArgList): Expr? {
        ParserLog.ds("Attempting proper prior check for $e1 with $argList")
        return try {
            Call(scope, Getter(e1), argList).validate()
        } catch (e: ValidationException) {
            ParserLog.ds("Proper prior failed due to ${e.message}")
            null
        }
    }

    private fun attemptThreePieceCombo(scope: Scope, e0: Expr, e1: Sym, e2: Expr): Expr? {
        // Try combo with previous
        ParserLog.ds("Checking combo")
        return try {
            checkForProperPrior(
                scope,
                Getter(e0, e1).patchType(scope, true),
                e2
            )
        } catch (e: TypeInferno) {
            null
        }
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
                        ArgList(scope, listOf(RawList.Prop(e3)))
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