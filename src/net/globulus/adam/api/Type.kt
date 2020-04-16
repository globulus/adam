package net.globulus.adam.api

interface Type

class Blockdef(val gens: GenList?,
               val rec: Sym?,
               val args: StructList?,
               val ret: Type
) : Type {
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

