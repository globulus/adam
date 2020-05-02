package net.globulus.adam.frontend.compiler

import net.globulus.adam.api.Sym
import java.nio.ByteBuffer

class Decompiler(val input: CompilerOutput) {

    private val buffer = ByteBuffer.wrap(input.byteCode)

    private var working = true
    private var il = 0 // indentation level

    fun decompile() {
        while (working) {
            decompOpCode()
        }
    }

    private fun decompOpCode(addNewline: Boolean = true): OpCode {
        val opCode = OpCode.from(nextByte)
        printIndent()
        print("$opCode ")
        when (opCode) {
            OpCode.DEF -> decompDef()
            OpCode.LOAD -> decompLoad()
            OpCode.GET -> decompGet()
            OpCode.CONST -> decompConst()
            OpCode.CALL -> decompCall()
            OpCode.ARGS -> decompArgs()
            OpCode.HALT -> working = false
        }
        if (addNewline && opCode != OpCode.ARGS) {
            println()
        }
        return opCode
    }

    private fun decompDef() {
        val spec = OpSpecifier.from(nextByte)
        print("$spec ")
        when (spec) {
            OpSpecifier.DEF_STR, OpSpecifier.DEF_NUM, OpSpecifier.DEF_ELSE -> print("$nextSym ")
            else -> throw IllegalStateException("Invalid opSpec for def $spec")
        }
    }

    private fun decompLoad() {
        print("$nextSym ")
    }

    private fun decompGet() {
        print("$nextSym ")
    }

    private fun decompConst() {
        val spec = OpSpecifier.from(nextByte)
        print("$spec ")
        when (spec) {
            OpSpecifier.CONST_INT -> print("${buffer.long} ")
            OpSpecifier.CONST_FLOAT -> print("${buffer.double} ")
            OpSpecifier.CONST_STR -> print("${input.strTable[nextInt]} ")
            else -> throw IllegalStateException("Invalid opSpec for const $spec")
        }
    }

    private fun decompCall() {
        il++
        println()
        decompOpCode()
        var opCode: OpCode
        do {
            opCode = decompOpCode()
        } while (opCode != OpCode.ARGS)
        il--
    }

    private fun decompArgs() {
        il++
        println()
        val len = nextInt
        for (i in 0 until len) {
            decompOpCode(i < len - 1)
        }
        il--
    }

    private val nextByte: Byte get() = buffer.get()

    private val nextInt: Int get() = buffer.int

    private val nextSym: Sym get() = input.symTable[nextInt]

    private fun printIndent() {
        print("  ".repeat(il))
    }
}