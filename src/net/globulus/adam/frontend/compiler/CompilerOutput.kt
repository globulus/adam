package net.globulus.adam.frontend.compiler

import net.globulus.adam.api.Sym

class CompilerOutput(val byteCode: ByteArray,
                     val symTable: Array<Sym>,
                     val strTable: Array<String>
)