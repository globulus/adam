package net.globulus.adam.api

import net.globulus.adam.frontend.parser.Scope
import net.globulus.adam.frontend.parser.TypeInfernal
import net.globulus.adam.frontend.parser.TypeInferno

interface Value : Expr, Type {
    override fun eval(args: ArgList?): Value {
        return this
    }
}

class Sym(val value: String) : Value {
    override var type: Type? = null

    override fun toString(): String {
        return value
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
    fun patchType(scope: Scope, patchToSelf: Boolean): Sym {
        type = try {
            TypeInfernal.infer(scope, this)
        } catch (e: TypeInferno) {
            if (patchToSelf) {
                this
            } else {
                throw e
            }
        }
        return this
    }

    companion object {
        val EMPTY = Sym("")
    }
}

class Str(val value: String) : Value {
    override var type: Type? = null

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

class Num(val value: Double) : Value {
    override var type: Type? = null

    override fun toString(): String {
        return value.toString()
    }

    override fun equals(other: Any?): Boolean {
        return value == (other as? Num)?.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}