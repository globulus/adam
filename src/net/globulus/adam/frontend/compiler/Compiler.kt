package net.globulus.adam.frontend.compiler

import net.globulus.adam.api.*
import net.globulus.adam.frontend.parser.ParserOutput
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class Compiler(private val input: ParserOutput) {

    private val byteCode: ByteArrayOutputStream = ByteArrayOutputStream()

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
        return CompilerOutput(byteCode.toByteArray(), symList.toTypedArray(), strList.toTypedArray())
    }

    private fun emitScope(scope: Scope) {
        byteCode.put(OpCode.PUSH_SCOPE)
        for ((sym, type) in scope.typeAliases) {
            emitDef(sym, type)
        }
    }

    private fun emitExpr(expr: Expr) {
        when (expr) {
            is Sym -> emitSym(expr)
            is Num -> emitNum(expr)
            is Str -> emitStr(expr)
            is Block -> emitBlock(expr)
            is Getter -> emitGet(expr)
            is Call -> emitCall(expr)
//            else -> throw IllegalArgumentException("Unable to emit expr $expr")
        }
    }

    private fun emitDef(sym: Sym, type: Type) {
        with(byteCode) {
            put(OpCode.DEF)
            putInt(sym.index)
            emitType(type)
        }
    }

    private fun emitType(type: Type) {
        with(byteCode) {
            when (type) {
                is Sym -> {
                    put(OpCode.SYM)
                    putInt(type.index)
                }
                is Blockdef -> emitBlockdef(type)
                is StructList -> {
                    put(if (type.gens != null) OpCode.TYPE_LIST_WITH_GENS else OpCode.TYPE_LIST)
                    emitStructList(type)
                }
                is Vararg -> {
                    put(OpCode.TYPE_VARARG)
                    emitType(type.embedded)
                }
                is Optional -> {
                    put(OpCode.TYPE_OPTIONAL)
                    emitType(type.embedded)
                }
                else -> throw IllegalArgumentException("Unable to emit type for $type")
            }
        }
    }

    private fun emitBlockdef(blockdef: Blockdef) {
        with(byteCode) {
            if (blockdef.gens == null && blockdef.rec == null) {
                put(OpCode.TYPE_BLOCK)
            } else if (blockdef.gens != null && blockdef.rec != null) {
                put(OpCode.TYPE_BLOCK_WITH_GENS_AND_REC)
            } else if (blockdef.gens != null) {
                put(OpCode.TYPE_BLOCK_WITH_GENS)
            } else {
                put(OpCode.TYPE_BLOCK_WITH_REC)
            }
            blockdef.args?.let {
                emitStructList(it)
            } ?: write(0)
            emitType(blockdef.ret)
            blockdef.gens?.let {
                emitGenList(it)
            }
            blockdef.rec?.let {
                emitType(it)
            }
        }
    }

    private fun emitStructList(list: StructList) {
        with(byteCode) {
            write(list.props.size)
            for (prop in list.props) {
                emitType(prop.type)
                putInt(prop.sym.index)
                prop.expr?.let { expr ->
                    put(OpCode.ARG_EXPR)
                    emitExpr(expr)
                }
            }
            list.gens?.let {
                emitGenList(it)
            }
        }
    }

    private fun emitGenList(list: GenList) {
        with(byteCode) {
            write(list.props.size)
            for (prop in list.props) {
                putInt(prop.sym.index) // TODO add superType
            }
        }
        println()
    }

    private fun emitSym(sym: Sym) {
        with(byteCode) {
            put(OpCode.SYM)
            putInt(sym.index)
        }
    }

    private fun emitNum(num: Num) {
        with(byteCode) {
            if (num.doubleValue != null) {
                put(OpCode.CONST_FLOAT)
                putDouble(num.doubleValue)
            } else {
                put(OpCode.CONST_INT)
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
            put(OpCode.CONST_STR)
            putInt(strIndex)
        }
    }

    private fun emitBlock(block: Block) {
        with(byteCode) {
            put(OpCode.BLOCK)
            block.args?.let {
                emitStructList(it)
            } ?: write(0)
            emitType(block.ret)
            putInt(block.body.size)
            for (line in block.body) {
                emitExpr(line)
            }
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
        emitGet(call.op)
        with(byteCode) {
            put(OpCode.CALL)
            write(call.args.props.size)
            for (prop in call.args.props) {
                emitExpr(prop.expr)
            }
        }
    }

//    private fun ByteBuffer.put(opCode: OpCode) {
//        put(opCode.byte)
//    }

    private fun ByteArrayOutputStream.put(opCode: OpCode) {
        write(opCode.byte.toInt())
    }

    private fun ByteArrayOutputStream.putInt(i: Int) {
        write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(i).array())
    }

    private fun ByteArrayOutputStream.putLong(l: Long) {
        write(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(l).array())
    }

    private fun ByteArrayOutputStream.putDouble(d: Double) {
        write(ByteBuffer.allocate(8).putDouble(d).array())
    }

    private val Sym.index: Int get() {
        return symTable[this] ?: run {
            symTable[this] = symCount++
            symList += this
            symCount - 1
        }
    }
}