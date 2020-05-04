package net.globulus.kadam

import net.globulus.adam.api.Sym
import net.globulus.adam.frontend.compiler.*
import java.nio.ByteBuffer
import java.util.*

/**
 * Transforms compiler bytecode into Kadam bytecode.
 */
class Transformer(private val input: CompilerOutput) {
    private val inputBuffer = ByteBuffer.wrap(input.byteCode)
    private lateinit var outputBuffer: CompilerOutputBuffer
    private lateinit var stack: Stack<Any>
    private var working = true

    fun transform(): CompilerOutput {
        inputBuffer.rewind()
        outputBuffer = CompilerOutputBuffer()
        stack = Stack()
        working = true
        while (working) {
            decompOpCode()
        }
        return outputBuffer.build()
    }

    private fun decompOpCode(isDef: Boolean = false): OpCode {
        val opCode = nextOpCode
        when (opCode) {
            OpCode.DEF -> {
                nextSym
                decompOpCode(true)
            }
            OpCode.TYPE_BLOCK, OpCode.TYPE_BLOCK_WITH_GENS, OpCode.TYPE_BLOCK_WITH_REC,
            OpCode.TYPE_BLOCK_WITH_GENS_AND_REC -> decompBlockdef(opCode)
            OpCode.TYPE_LIST, OpCode.TYPE_LIST_WITH_GENS -> decompStructList(opCode)
            OpCode.TYPE_VARARG, OpCode.TYPE_OPTIONAL -> decompOpCode(true) // Decomp embedded
            OpCode.SYM -> decompSym(isDef)
            OpCode.GET -> decompGet()
            OpCode.CONST_FLOAT, OpCode.CONST_INT, OpCode.CONST_STR -> decompConst(opCode)
            OpCode.BLOCK -> decompBlock()
            OpCode.CALL -> decompCall()
            OpCode.HALT -> working = false
        }
        return opCode
    }

    private fun decompBlockdef(opCode: OpCode) { // Unsupported
        val argLen = nextByte
        if (argLen > 0) {
            decompStructList(opCode, argLen)
        }
        decompOpCode(true) // ret
        if (opCode == OpCode.TYPE_BLOCK_WITH_GENS || opCode == OpCode.TYPE_BLOCK_WITH_GENS_AND_REC) {
            decompGenList()
        }
        if (opCode == OpCode.TYPE_BLOCK_WITH_REC || opCode == OpCode.TYPE_BLOCK_WITH_GENS_AND_REC) {
            decompOpCode(true)
        }
    }

    private fun decompStructList(opCode: OpCode, readLen: Byte? = null) { // Unsupported
        val len = readLen ?: nextByte
        for (i in 0 until len) {
            decompOpCode(true)
            nextSym
            if (nextOpCode == OpCode.ARG_EXPR) {
                decompOpCode(true)
            } else {
                rollBack()
            }
        }
        if (opCode == OpCode.TYPE_LIST_WITH_GENS) {
            decompGenList()
        }
    }

    private fun decompGenList() { // Unsupported
        val len = nextByte
        for (i in 0 until len) {
            nextSym
        }
    }

    private fun decompSym(isDef: Boolean) {
        val sym = nextSym
        if (!isDef) {
            stack.push(sym)
        }
    }

    private fun decompGet() {
        val name = nextSym.value
        when (name) {
            PRINT -> when (val peek = stack.peek()) {
                is String -> stack.push(PRINT_STR)
                else -> stack.push(PRINT_NUM) // TODO fix using actual type lookup
            }
            else -> stack.push(name)
        }
    }

    private fun decompConst(opCode: OpCode) {
        when (opCode) {
            OpCode.CONST_INT -> stack.push(inputBuffer.long)
            OpCode.CONST_FLOAT -> stack.push(inputBuffer.double)
            OpCode.CONST_STR -> stack.push(input.strTable[nextInt])
            else -> throw IllegalArgumentException()
        }
    }

    private fun decompBlock() { // Unsupported, skips
        val skipLen = nextInt
        inputBuffer.position(skipLen)
//        print("to skip goto: $skipLen ")
//        val argLen = nextByte
//        if (argLen > 0) {
//            decompStructList(OpCode.BLOCK, argLen)
//        }
//        decompOpCode(false) // ret
//        val bodyLen = nextInt
//        for (i in 0 until bodyLen) {
//            decompOpCode()
//        }
    }

    private fun decompCall() {
        val name = when (val top = stack.pop()) {
            is Sym -> top.value
            is String -> top
            else -> throw IllegalStateException("Wrong type at the top of stack when calling: $top")
        }
        val mainStack = stack
        val argStack = Stack<Any>()
        stack = argStack
        val len = nextByte
        for (i in 0 until len) {
            val skipPos = nextInt
            while (inputBuffer.position() < skipPos) {
                decompOpCode()
            }
        }
        stack = mainStack
        with(outputBuffer.byteCode) {
            when (name) {
                PRINT_STR -> {
                    emitString(stack.pop() as String)
                    put(KadamOpCode.PRINT)
                    val a = 5
                }
                PRINT_NUM -> {
                    emitLoad(stack.pop() as Sym)
                    put(KadamOpCode.PRINT)
                    val a = 5
                }
                "=" -> {
                    if (argStack.isNotEmpty()) {
                        emitGetValue(argStack.pop())
                    }
                    emitSym(stack.pop() as Sym)
                    put(KadamOpCode.STORE_VAR)
                    val a = 5
                }
                "+" -> {
                    if (argStack.isNotEmpty()) {
                        emitGetValue(argStack.pop())
                    }
                    emitGetValue(stack.pop())
                    put(KadamOpCode.ADD)
                    val a = 5
                }
            }
        }
    }

    private fun emitGetValue(top: Any) {
        when (top) {
            is Sym -> emitLoad(top)
            is Long -> emitInt(top)
            is Double -> emitFloat(top)
            is String -> emitString(top)
            else -> throw IllegalArgumentException("Unable to emit get value for $top")
        }
    }

    private fun emitLoad(sym: Sym) {
        with(outputBuffer.byteCode) {
            emitSym(sym)
            put(KadamOpCode.LOAD)
        }
    }

    private fun emitSym(sym: Sym) {
        with(outputBuffer.byteCode) {
            put(KadamOpCode.PUSH_SYM)
            putInt(outputBuffer.symIndex(sym))
        }
    }

    private fun emitInt(l: Long) {
        with(outputBuffer.byteCode) {
            put(KadamOpCode.PUSH_INT)
            putLong(l)
        }
    }

    private fun emitFloat(d: Double) {
        with(outputBuffer.byteCode) {
            put(KadamOpCode.PUSH_FLOAT)
            putDouble(d)
        }
    }

    private fun emitString(s: String) {
        with(outputBuffer.byteCode) {
            put(KadamOpCode.PUSH_STR)
            putInt(outputBuffer.strIndex(s))
        }
    }

    private val nextOpCode: OpCode get() = OpCode.from(nextByte)

    private val nextByte: Byte get() = inputBuffer.get()

    private val nextInt: Int get() = inputBuffer.int

    private val nextSym: Sym get() = input.symTable[nextInt]

    private fun rollBack() {
        inputBuffer.position(inputBuffer.position() - 1)
    }

    companion object {
        private const val PRINT = "print"
        private const val PRINT_STR = "printStr"
        private const val PRINT_NUM = "printNum"
    }
}