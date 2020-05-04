package net.globulus.kadam

import net.globulus.adam.api.Sym
import net.globulus.adam.frontend.compiler.CompilerOutput
import java.nio.ByteBuffer
import java.util.*

class Vm(private val input: CompilerOutput) {
    private val buffer = ByteBuffer.wrap(input.byteCode)
    private lateinit var stack: Stack<Any>
    private val vars = mutableMapOf<Sym, Long>()

    fun interpret() {
        buffer.rewind()
        stack = Stack()
        while (buffer.hasRemaining()) {
            interpretOpCode()
        }
    }

    private fun interpretOpCode() {
        when (nextOpCode) {
            KadamOpCode.PUSH_SYM -> stack.push(nextSym)
            KadamOpCode.PUSH_INT -> stack.push(buffer.long)
            KadamOpCode.PUSH_FLOAT -> stack.push(buffer.double)
            KadamOpCode.PUSH_STR -> stack.push(input.strTable[nextInt])
            KadamOpCode.LOAD -> stack.push(vars[stack.pop() as Sym])
            KadamOpCode.STORE_VAR -> vars[stack.pop() as Sym] = stack.pop() as Long
            KadamOpCode.ADD -> stack.push(stack.pop() as Long + stack.pop() as Long)
            KadamOpCode.PRINT -> println(stack.pop())
        }
    }

    private val nextOpCode: KadamOpCode get() = KadamOpCode.from(nextByte)

    private val nextByte: Byte get() = buffer.get()

    private val nextInt: Int get() = buffer.int

    private val nextSym: Sym get() = input.symTable[nextInt]
}