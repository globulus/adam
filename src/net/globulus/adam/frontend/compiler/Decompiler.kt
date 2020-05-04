package net.globulus.adam.frontend.compiler

import net.globulus.adam.api.Sym
import java.nio.ByteBuffer

class Decompiler(private val input: CompilerOutput) {
    private val buffer = ByteBuffer.wrap(input.byteCode)

    private var working = true
    private var il = 0 // indentation level

    fun decompile() {
        while (working) {
            decompOpCode()
        }
    }

    private fun decompOpCode(addNewline: Boolean = true): OpCode {
        val opCode = nextOpCode
        printIndent()
        print("$opCode ")
        when (opCode) {
            OpCode.DEF -> {
                print("$nextSym ")
                decompOpCode(false)
            }
//            OpCode.TYPE_ALIAS -> print("$nextSym ")
            OpCode.TYPE_BLOCK, OpCode.TYPE_BLOCK_WITH_GENS, OpCode.TYPE_BLOCK_WITH_REC,
                OpCode.TYPE_BLOCK_WITH_GENS_AND_REC -> decompBlockdef(opCode)
            OpCode.TYPE_LIST, OpCode.TYPE_LIST_WITH_GENS -> decompStructList(opCode)
            OpCode.TYPE_VARARG, OpCode.TYPE_OPTIONAL -> decompOpCode(false) // Decomp embedded
            OpCode.SYM -> decompSym()
            OpCode.GET -> decompGet()
            OpCode.CONST_FLOAT, OpCode.CONST_INT, OpCode.CONST_STR -> decompConst(opCode)
            OpCode.BLOCK -> decompBlock()
            OpCode.CALL -> decompCall()
            OpCode.HALT -> working = false
        }
        if (addNewline) {
            println()
        }
        return opCode
    }

    private fun decompBlockdef(opCode: OpCode) {
        il++
        val argLen = nextByte
        if (argLen > 0) {
            decompStructList(opCode, argLen)
        }
        decompOpCode() // ret
        if (opCode == OpCode.TYPE_BLOCK_WITH_GENS || opCode == OpCode.TYPE_BLOCK_WITH_GENS_AND_REC) {
            decompGenList()
        }
        if (opCode == OpCode.TYPE_BLOCK_WITH_REC || opCode == OpCode.TYPE_BLOCK_WITH_GENS_AND_REC) {
            decompOpCode()
        }
        il--
    }

    private fun decompStructList(opCode: OpCode, readLen: Byte? = null) {
        val len = readLen ?: nextByte
        print("$len ")
        il++
        if (len > 0) {
            println()
        }
        for (i in 0 until len) {
            decompOpCode(false)
            printIndent()
            print("$nextSym ")
            if (nextOpCode == OpCode.ARG_EXPR) {
                decompOpCode(false)
            } else {
                rollBack()
            }
            println()
        }
        if (opCode == OpCode.TYPE_LIST_WITH_GENS) {
            decompGenList()
        }
        il--
    }

    private fun decompGenList() {
        val len = nextByte
        printIndent()
        print("gens: ")
        for (i in 0 until len) {
            print("$nextSym ")
        }
        println()
    }

    private fun decompSym() {
        print("$nextSym ")
    }

    private fun decompGet() {
        print("$nextSym ")
    }

    private fun decompConst(opCode: OpCode) {
        when (opCode) {
            OpCode.CONST_INT -> print("${buffer.long} ")
            OpCode.CONST_FLOAT -> print("${buffer.double} ")
            OpCode.CONST_STR -> print("${input.strTable[nextInt]} ")
        }
    }

    private fun decompBlock() {
        val skipLen = nextInt
        print("to skip goto: $skipLen ")
        val argLen = nextByte
        if (argLen > 0) {
            decompStructList(OpCode.BLOCK, argLen)
        }
        decompOpCode(false) // ret
        val bodyLen = nextInt
        il++
        for (i in 0 until bodyLen) {
            decompOpCode()
        }
        il--
    }

    private fun decompCall() {
        il++
        val len = nextByte
        print("$len")
        if (len > 0) {
            println()
        }
        for (i in 0 until len) {
            val skipPos = nextInt
            println("arg $i skip at $skipPos")
            while (buffer.position() < skipPos) {
                decompOpCode(i < len - 1)
            }
        }
        il--
    }

    private val nextOpCode: OpCode get() = OpCode.from(nextByte)

    private val nextByte: Byte get() = buffer.get()

    private val nextInt: Int get() = buffer.int

    private val nextSym: Sym get() = input.symTable[nextInt]

    private fun rollBack() {
        buffer.position(buffer.position() - 1)
    }

    private fun printIndent() {
        print("  ".repeat(il))
    }
}