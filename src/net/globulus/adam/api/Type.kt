package net.globulus.adam.api

interface Type

class Blockdef(val gens: GenList?,
               val rec: Sym?,
               val args: StructList?,
               val ret: Type) : Type {
    override fun toString(): String {
        return StringBuilder().apply {
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
    override fun toString(): String {
        return "$embedded..."
    }
}

class GensTable {
    private val syms = mutableSetOf<Sym>()
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
}

