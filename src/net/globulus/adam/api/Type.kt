package net.globulus.adam.api

interface Type {
    var alias: Sym?
    fun replacing(genTable: GenTable): Type
    infix fun matches(other: Type?) = this == other
    infix fun doesntMatch(other: Type?) = !matches(other)
}

class Blockdef(
    var gens: GenList?,
    val rec: Sym?,
    val args: StructList?,
    val ret: Type) : Type {
    override var alias: Sym? = null

    override fun replacing(genTable: GenTable): Type {
        return Blockdef(gens, rec?.replacing(genTable) as? Sym, args?.replacing(genTable) as? StructList, ret.replacing(genTable))
    }

    override fun toString(): String {
        return alias?.toString() ?: StringBuilder().apply {
            gens?.let {
                append(gens.toString())
                append("..")
            }
            rec?.let {
                append(rec.toString())
                append(".")
            }
            args?.let {
                append("{")
                append(args.toString())
            }
            append(ret.toString())
            if (args != null) {
                append("}")
            }
        }.toString()
    }
}

class Vararg(val embedded: Type) : Type {
    override var alias: Sym? = null
    override fun replacing(genTable: GenTable): Type {
        return Vararg(embedded.replacing(genTable))
    }

    override fun matches(other: Type?): Boolean {
        if (this == other) {
            return true
        }
        if (other !is Vararg) {
            return false
        }
        return embedded matches other.embedded
    }

    override fun toString(): String {
        return "${alias ?: embedded}..."
    }
}

class GenTable {
    internal val syms = mutableSetOf<Sym>()
    private val types = mutableMapOf<Sym, Type>()

    val size get() = syms.size

    fun set(sym: Sym) {
        syms += sym
    }

    operator fun set(sym: Sym, type: Type) {
        types[sym] = type
        syms += sym
    }

    operator fun get(sym: Sym): Type {
        return types[sym]?.let { it }
            ?: throw if (sym in syms) {
                IllegalStateException("Uninferred generic type $sym")
            } else {
                UnsupportedOperationException("Why are you even getting this? $sym")
            }
    }

    operator fun contains(sym: Sym) = sym in syms
}

