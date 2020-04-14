package net.globulus.adam.api

import java.util.*

interface Expr {
    fun eval(args: ArgList? = null): Value
}

class Block(val args: StructList?,
            val ret: Type?,
            val body: List<Expr>
) : Expr {
    override fun eval(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

}

class Call(val op: Expr, val args: ArgList) : Expr {
    override fun eval(args: ArgList?): Value {
        TODO("Not yet implemented")
    }
}

class Getter(val syms: Queue<Sym>) : Expr {
    override fun eval(args: ArgList?): Value {
        TODO("Not yet implemented")
    }

}