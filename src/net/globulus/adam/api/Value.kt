package net.globulus.adam.api

interface Value : Type

class Sym(val value: String) : Expr(), Value {

    constructor(scope: Scope, value: String) : this(value) {
        this.scope = scope
    }

    override var alias: Sym? = null
    override lateinit var scope: Scope
    override var type: Type = this
    var gens: List<Sym>? = null // ifBranching..[T]

    override fun replacing(genTable: GenTable): Type {
        for (genSym in genTable.syms) {
            if (genSym == this) {
                return genTable[genSym]
            }
        }
        return this
    }

    override fun toValue(args: ArgList?): Value {
        return this
    }

    override fun toString(): String {
        return alias?.toString()
            ?: StringBuilder(value).apply {
                gens?.let {
                    append("..")
                    append(it.joinToString(", ", "[" , "]"))
                }
            }.toString()
    }

    override fun equals(other: Any?): Boolean {
        return value == (other as? Sym)?.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    /**
     * @param patchToSelf Ignore TypeInferno and patch to self for cases where we're looking at a singular, free-standing
     * sym that's a desugaring candidate. For [Getter]s, this is set to false as we need to know the type of the origin.
     */
//    fun patchType(scope: Scope, patchToSelf: Boolean): Sym {
//        type = try {
//            TypeInfernal.infer(scope, this, true)
//        } catch (e: TypeInferno) {
//            if (patchToSelf) {
//                this
//            } else {
//                throw e
//            }
//        }
//        return this
//    }

    companion object {
        fun empty(scope: Scope) = Sym("").apply { this.scope = scope }
    }
}

class Str(val value: String) : Expr(), Value {
    override var alias: Sym? = null
    override lateinit var type: Type
    override lateinit var scope: Scope

    override fun replacing(genTable: GenTable): Type {
        return this
    }

    override fun toValue(args: ArgList?): Value {
        return this
    }

    override fun toString(): String {
        return "\"$value\""
    }

    override fun equals(other: Any?): Boolean {
        return value == (other as? Str)?.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

class Num(val doubleValue: Double?, val longValue: Long?) : Expr(), Value {

    val isInt = (doubleValue == null)

    override var alias: Sym? = null
    override lateinit var type: Type
    override lateinit var scope: Scope

    override fun replacing(genTable: GenTable): Type {
        return this
    }

    override fun toValue(args: ArgList?): Value {
        return this
    }

    override fun toString(): String {
        return (doubleValue ?: longValue!!).toString()
    }

    override fun equals(other: Any?): Boolean {
        return (other as? Num)?.let {
            doubleValue == it.doubleValue && longValue == it.longValue
        } ?: false
    }

    override fun hashCode(): Int {
        return (doubleValue ?: longValue!!).hashCode()
    }
}