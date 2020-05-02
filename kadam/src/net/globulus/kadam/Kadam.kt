package net.globulus.kadam

import net.globulus.adam.adam

fun main(args: Array<String>) {
    val output = adam(args[0])
    val interpreter = Interpeter()
    interpreter.interpret(output.rootScope, output.exprs)
}