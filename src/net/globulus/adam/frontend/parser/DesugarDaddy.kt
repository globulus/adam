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
                        attemptWithE0 = attemptThreePieceCombo(scope, e0, e1, e2)
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
                                Getter(eMinus1, e0).patchType(scope),
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
//                            throw ValidationException("Standalone block detected, unable to tie it to any calls!")
                            ParserLog.ds("4-piece combo failed, bailing")
                            desugared += e1
                        }
                    } else {
//                        throw ValidationException("Standalone block detected, unable to tie it to any calls!")
                        ParserLog.ds("3-piece combo failed, bailing")
                        desugared += e1
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
            Call(scope, if (e1 is Getter) e1 else Getter(e1).patchType(scope), argList).validate()
        } catch (e: Exception) {
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
                Getter(e0, e1).patchType(scope),
                e2
            )
        } catch (e: Exception) {
            ParserLog.ds("3-piece combo failed due to ${e.message}")
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
        return try {
            Call(scope,
                    Getter(e2, e1 as Sym).patchType(scope),
                    ArgList(scope)
            ).validate()
        } catch (e: Exception) {
            ParserLog.ds("Unary desugar failed due to ${e.message}")
            null
        }
//        reduceToSym(e1)?.let { e1Sym ->
//            val e2Type = TypeInfernal.tryToInferList(scope, e2)
//            e2Type?.let { list ->
//                try {
//                    val memberType = TypeInfernal.tryToInferBlockdefFromList(scope, list, e1Sym)
//                    memberType?.let {
//                        return if (it.args?.props?.isNotEmpty() == true) {
//                            ParserLog.ds("Member is a non-primitive blockdef: $it, bailing")
//                            null
//                        } else {
//                            ParserLog.ds("Found primitive member $e1 of type $memberType on $e2, returning unary desugar")
//                            Call(scope,
//                                Getter(e2, e1Sym).patchType(scope, true),
//                                ArgList(scope, emptyList())
//                            ).validate()
//                        }
//                    } ?: run {
//                        ParserLog.ds("Member is not a Blockdef: $memberType, bailing")
//                        return null
//                    }
//                } catch (e: TypeInferno) {
//                    ParserLog.ds("Member not found for $e1, bailing")
//                    return null
//                }
//            } ?: run {
//                ParserLog.ds("E2 isn't a list but $e2Type, bailing")
//                return null
//            }
//        } ?: run {
//            ParserLog.ds("E1 isn't a Sym but ${e1::class.simpleName} $e1, bailing")
//            return null
//        }
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
        val argList = ArgList(scope, e3)
        return try {
            Call(scope, Getter(e1, e2 as Sym).patchType(scope), argList).validate()
        } catch (e: Exception) {
            ParserLog.ds("Binary desugar failed due to ${e.message}, will attempt generic rec")
            try {
                val e2Blockdef = TypeInfernal.tryToInferBlockdef(scope, e2)
                val e1Type = TypeInfernal.infer(scope, e1)
                val typeGenTablePair = attemptToInferRecGen(scope, e2Blockdef!!, e1Type)
                if (typeGenTablePair == null) {
                    ParserLog.ds("Rec type ${e2Blockdef.rec} is different than E1 type $e1Type, bailing")
                    null
                } else {
                    Call(scope,
                            Getter(e1, e2 as Sym).apply {
                                type = Blockdef(scope, e2Blockdef.gens, typeGenTablePair.type, e2Blockdef.args, e2Blockdef.ret)
                            },
                            argList
                    ).apply {
                        genTable = typeGenTablePair.genTable
                    }.validate()
                }
            } catch (e: Exception) {
                ParserLog.ds("Binary desugar failed due to ${e.message}")
                null
            }
        }
//        reduceToSym(e2)?.let { e2Sym ->
//            val e1Type = TypeInfernal.tryToInferList(scope, e1)
//            e1Type?.let { list ->
//                return try {
//                    val memberType = TypeInfernal.tryToInferBlockdefFromList(scope, list, e2Sym)
//                    desugarBinaryOnFoundMember(scope, e1, e2Sym, memberType, e3)
//                } catch (e: TypeInferno) {
//                    ParserLog.ds("Member not found for $e2Sym on $list, will attempt blockdef with rec")
//                    attemptBinaryOnBlockdefWithRec(scope, e1, e1Type, e2Sym, e3)
//                }
//            } ?: run {
//                ParserLog.ds("E1 isn't a list but $e1Type, will attempt blockdef with rec")
//                return attemptBinaryOnBlockdefWithRec(scope, e1, TypeInfernal.bottomMostType(scope, e1Type), e2Sym, e3)
//            }
//        } ?: run {
//            ParserLog.ds("E2 isn't a symbol but ${e2::class.simpleName} $e2")
//            return null
//        }
    }

//    private fun attemptBinaryOnBlockdefWithRec(scope: Scope, e1: Expr, e1Type: Type, e2Sym: Sym, e3: Expr): Expr? {
//        val e2Type = TypeInfernal.tryToInferBlockdef(scope, e2Sym)
//        e2Type?.let { e2Blockdef ->
//            e2Blockdef.rec?.let { e2RecType ->
//                var genTable: GenTable? = null
//                var recType = if (e2RecType is Sym) TypeInfernal.infer(scope, e2RecType, true) else e2RecType
//                if (recType doesntMatch e1Type) {
//                    val typeGenTablePair = attemptToInferRecGen(scope, e2Blockdef, e1Type)
//                    if (typeGenTablePair == null) {
//                        ParserLog.ds("Rec type $recType is different than E1 type $e1Type, bailing")
//                        return null
//                    } else {
//                        recType = typeGenTablePair.type
//                        genTable = typeGenTablePair.genTable
//                    }
//                }
//                val blockdef = Blockdef(e2Blockdef.gens, recType, e2Blockdef.args, e2Blockdef.ret)
//                return desugarBinaryOnFoundMember(scope, e1, e2Sym, blockdef, e3, genTable)
//            } ?: run {
//                ParserLog.ds("E2 blockdef doesn't have a rec, bailing")
//                return null
//            }
//        } ?: run {
//            ParserLog.ds("E2 isn't a blockdef but $e2Sym, bailing")
//            return null
//        }
//    }
//
//    private fun desugarBinaryOnFoundMember(scope: Scope,
//                                           e1: Expr,
//                                           e2Sym: Sym,
//                                           memberType: Blockdef?,
//                                           e3: Expr,
//                                           genTable: GenTable? = null
//    ): Expr? {
//        memberType?.let {
//            return if (it.args?.props?.size == 1) {
//                val e3Type = e3.type
//                var auxGenTable = genTable
//                var firstArgType = it.args.props[0].type
//                if (firstArgType is Sym) {
//                    // This handles the case when the return type of a block is still stored as Num
//                    // when it fact it should be identified as StructList, or if it's an uniferred gen
//                    try {
//                        firstArgType = TypeInfernal.whatIsThisSymAliasFor(scope, firstArgType)
//                    } catch (e: UndefinedSymException) {
//                        // If the sym couldn't be inferred, it might be because it's actually a gen type
//                        if (it.gens?.contains(firstArgType) == true) {
//                            ParserLog.ds("First arg type is actually a gen, inferring $firstArgType as ${e3.type}")
//                            if (auxGenTable == null) {
//                                auxGenTable = GenTable()
//                            }
//                            auxGenTable[firstArgType] = e3.type
//                            firstArgType = e3.type
//                        } else {
//                            throw e
//                        }
//                    }
//                }
//                if (e3Type matches firstArgType) {
//                    ParserLog.ds("Returning binary desugar")
//                    Call(scope,
//                        Getter(e1, e2Sym).apply { type = memberType },
//                        ArgList(scope, listOf(RawList.Prop(e3)))
//                    ).apply {
//                        type = TypeInfernal.bottomMostType(scope, memberType)
//                        this.genTable = auxGenTable
//                    }.validate()
//                } else {
//                    ParserLog.ds("E3 type ${e3Type::class.simpleName} $e3Type doesn't match first arg type ${firstArgType::class.simpleName} $firstArgType")
//                    null
//                }
//            } else {
//                ParserLog.ds("Member is not a single-arg blockdef: $it, bailing")
//                null
//            }
//        } ?: run {
//            ParserLog.ds("Member is not a Blockdef: $memberType, bailing")
//            return null
//        }
//    }
//
//    private fun reduceToSym(expr: Expr): Sym? {
//        return when (expr) {
//            is Sym -> expr
//            is Getter -> expr.origin as? Sym
//            else -> null
//        }
//    }

    private fun attemptToInferRecGen(scope: Scope, blockdef: Blockdef, e1Type: Type): TypeGenTablePair? {
        if (blockdef.gens == null) {
            return null
        }
        return when (blockdef.rec) {
            is Optional -> {
                if (e1Type !is Optional) {
                    null
                } else {
                    val genTable = inferGenericRec(scope, blockdef.gens!!, blockdef.rec.embedded as Sym, e1Type.embedded)
                    if (genTable != null) {
                        TypeGenTablePair(Optional(e1Type), genTable)
                    } else {
                        null
                    }
                }
            }
            is Sym -> {
                val genTable = inferGenericRec(scope, blockdef.gens!!, blockdef.rec, e1Type)
                if (genTable != null) {
                    TypeGenTablePair(e1Type, genTable)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun inferGenericRec(scope: Scope, gens: GenList, rec: Sym, e1Type: Type): GenTable? {
        return if (rec in gens) {
            ParserLog.ds("Inferred rec type $rec as $e1Type")
            GenTable().apply {
                set(rec, if (e1Type is Sym) TypeInfernal.whatIsThisSymAliasFor(scope, e1Type, true) else e1Type)
            }
        } else {
            null
        }
    }

    private class TypeGenTablePair(val type: Type, val genTable: GenTable)
}