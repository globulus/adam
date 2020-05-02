package net.globulus.adam.frontend.compiler

import net.globulus.adam.api.*
import net.globulus.adam.frontend.parser.ParserOutput
import net.globulus.adam.frontend.parser.TypeInfernal
import java.nio.ByteBuffer

class Compiler(val input: ParserOutput) {

    private val byteCode: ByteBuffer = ByteBuffer.allocate(1024)

    private val symTable = mutableMapOf<Sym, Int>()
    private val symList = mutableListOf<Sym>()
    private var symCount = 0

    private val strTable = mutableMapOf<String, Int>()
    private val strList = mutableListOf<String>()
    private var strCount = 0

    fun compile(): CompilerOutput {
        emitScope(input.rootScope)
        for (expr in input.exprs) {
            emitExpr(expr)
        }
        byteCode.put(OpCode.HALT) // Reached end of program
        return CompilerOutput(byteCode.array(), symList.toTypedArray(), strList.toTypedArray())
    }

    private fun emitScope(scope: Scope) {
        for ((sym, type) in scope.typeAliases) {
            emitDef(sym, type)
        }
    }

    private fun emitExpr(expr: Expr) {
        when (expr) {
            is Sym -> emitLoad(expr)
            is Num -> emitNum(expr)
            is Str -> emitStr(expr)
            is Getter -> emitGet(expr)
            is Call -> emitCall(expr)
//            else -> throw IllegalArgumentException("Unable to emit expr $expr")
        }
    }

    private fun emitDef(sym: Sym, type: Type) {
        with(byteCode) {
            put(OpCode.DEF)
            when (type) {
                TypeInfernal.SYM_NUM -> put(OpSpecifier.DEF_NUM)
                TypeInfernal.SYM_STR -> put(OpSpecifier.DEF_STR)
                else -> put(OpSpecifier.DEF_ELSE)
            }
//                else -> throw IllegalArgumentException("Unable to get DEF_ OpSpecifier for $type")
            putInt(sym.index)
        }
    }

    private fun emitLoad(sym: Sym) {
        with(byteCode) {
            put(OpCode.LOAD)
            putInt(sym.index)
        }
    }

    private fun emitNum(num: Num) {
        with(byteCode) {
            put(OpCode.CONST)
            if (num.doubleValue != null) {
                put(OpSpecifier.CONST_FLOAT)
                putDouble(num.doubleValue)
            } else {
                put(OpSpecifier.CONST_INT)
                putLong(num.longValue!!)
            }
        }
    }

    private fun emitStr(str: Str) {
        val value = str.value
        val strIndex = strTable[value] ?: run {
            strTable[value] = strCount++
            strList += value
            strCount - 1
        }
        with(byteCode) {
            put(OpCode.CONST)
            put(OpSpecifier.CONST_STR)
            putInt(strIndex)
        }
    }

    private fun emitGet(getter: Getter) {
        emitExpr(getter.origin)
        with(byteCode) {
            for (sym in getter.syms) {
                put(OpCode.GET)
                putInt(sym.index)
            }
        }
    }

    private fun emitCall(call: Call) {
        with(byteCode) {
            put(OpCode.CALL)
            emitGet(call.op)
            put(OpCode.ARGS)
            putInt(call.args.props.size)
            for (prop in call.args.props) {
                emitExpr(prop.expr)
            }
        }
    }

    private fun ByteBuffer.put(opCode: OpCode) {
        put(opCode.byte)
    }

    private fun ByteBuffer.put(opSpecifier: OpSpecifier) {
        put(opSpecifier.byte)
    }

    private val Sym.index: Int get() {
        return symTable[this] ?: run {
            symTable[this] = symCount++
            symList += this
            symCount - 1
        }
    }
}