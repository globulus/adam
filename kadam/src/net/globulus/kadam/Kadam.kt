package net.globulus.kadam

import net.globulus.adam.adam
import net.globulus.adam.mark

fun main(args: Array<String>) {
    var timestamp = System.currentTimeMillis()
    val output = adam(args[0])
    timestamp = mark("Adam", timestamp)
    val transformer = Transformer(output)
    val transformOutput = transformer.transform()
    timestamp = mark("Transform", timestamp)
    val vm = Vm(transformOutput)
    vm.interpret()
    mark("Interpret", timestamp)
}