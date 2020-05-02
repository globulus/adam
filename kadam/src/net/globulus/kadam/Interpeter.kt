package net.globulus.kadam

import net.globulus.adam.api.*
import java.util.*

class Interpeter {

    private val envStack = Stack<Env>()

    fun interpret(rootScope: Scope, exprs: List<Expr>) {
        push(rootScope)
        for (expr in exprs) {
            work(expr)
        }
    }

    private fun work(expr: Expr): Value {
        return when (expr) {
            is Num -> expr
            is Str -> expr
            is Sym -> expr
            is AdamList<*> -> expr
            is Block -> runBlock(expr)
            is Call -> call(expr)
            is Getter -> work(expr.origin) // TODO fix
            else -> throw IllegalArgumentException("No se")
        }
    }

    private fun runBlock(block: Block): Value {
        push(block.bodyScope)
        var res: Value? = null
        for (expr in block.body) {
            res = work(expr)
        }
        envStack.pop()
        return res!!
    }

    private fun call(call: Call): Value {
        push(call.scope)
        println("CALL")
        val op = work(call.op)
//        if (op is Str) {
//            when (op.value) {
//                "print" -> println()
//            }
//        }
        println("getter $op")
        println("args ${call.args.props.map { work(it.expr) }.joinToString() }")
        envStack.pop()
        return op
    }

    private fun push(scope: Scope) {
        envStack.push(Env(scope))
    }
}

class Env(scope: Scope) {

}