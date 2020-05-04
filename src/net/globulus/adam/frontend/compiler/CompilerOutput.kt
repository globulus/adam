package net.globulus.adam.frontend.compiler

import net.globulus.adam.api.Str
import net.globulus.adam.api.Sym

class CompilerOutput(val byteCode: ByteArray,
                     val symTable: Array<Sym>,
                     val strTable: Array<String>
)

class CompilerOutputBuffer {
    var byteCode = mutableListOf<Byte>()
        private set

    private val symTable = mutableMapOf<Sym, Int>()
    private val symList = mutableListOf<Sym>()
    private var symCount = 0

    private val strTable = mutableMapOf<String, Int>()
    private val strList = mutableListOf<String>()
    private var strCount = 0

    fun build(): CompilerOutput {
        return CompilerOutput(byteCode.toByteArray(), symList.toTypedArray(), strList.toTypedArray())
    }

    fun strIndex(s: String): Int {
        return strTable[s] ?: run {
            strTable[s] = strCount++
            strList += s
            strCount - 1
        }
    }

    fun strIndex(str: Str): Int {
        return strIndex(str.value)
    }

    fun symIndex(sym: Sym): Int {
        return symTable[sym] ?: run {
            symTable[sym] = symCount++
            symList += sym
            symCount - 1
        }
    }
}