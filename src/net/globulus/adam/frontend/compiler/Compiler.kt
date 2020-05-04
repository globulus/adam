package net.globulus.adam.frontend.compiler

import net.globulus.adam.api.*
import net.globulus.adam.frontend.parser.ParserOutput

class Compiler(private val input: ParserOutput) {

    private val buffer = CompilerOutputBuffer()

    fun compile(): CompilerOutput {
        emitScope(input.rootScope)
        for (expr in input.exprs) {
            emitExpr(expr)
        }
        buffer.byteCode.put(OpCode.HALT) // Reached end of program
        return buffer.build()
    }

    private fun emitScope(scope: Scope) {
        buffer.byteCode.put(OpCode.PUSH_SCOPE)
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
        with(buffer.byteCode) {
            put(OpCode.DEF)
            putInt(sym.index)
            emitType(type)
        }
    }

    private fun emitType(type: Type) {
        with(buffer.byteCode) {
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
        with(buffer.byteCode) {
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
            } ?: put(0)
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
        with(buffer.byteCode) {
            put(list.props.size.toByte())
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
        with(buffer.byteCode) {
            put(list.props.size.toByte())
            for (prop in list.props) {
                putInt(prop.sym.index) // TODO add superType
            }
        }
    }

    private fun emitSym(sym: Sym) {
        with(buffer.byteCode) {
            put(OpCode.SYM)
            putInt(sym.index)
        }
    }

    private fun emitNum(num: Num) {
        with(buffer.byteCode) {
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
        with(buffer.byteCode) {
            put(OpCode.CONST_STR)
            putInt(buffer.strIndex(str))
        }
    }

    private fun emitBlock(block: Block) {
        with(buffer.byteCode) {
            put(OpCode.BLOCK)
            val skipPos = buffer.byteCode.size
            putInt(0)
            block.args?.let {
                emitStructList(it)
            } ?: put(0)
            emitType(block.ret)
            putInt(block.body.size)
            for (line in block.body) {
                emitExpr(line)
            }
            setInt(buffer.byteCode.size, skipPos)
        }
    }

    private fun emitGet(getter: Getter) {
        emitExpr(getter.origin)
        with(buffer.byteCode) {
            for (sym in getter.syms) {
                put(OpCode.GET)
                putInt(sym.index)
            }
        }
    }

    private fun emitCall(call: Call) {
        emitGet(call.op)
        with(buffer.byteCode) {
            put(OpCode.CALL)
            put(call.args.props.size.toByte())
            for (prop in call.args.props) {
                val skipPos = buffer.byteCode.size
                putInt(0)
                emitExpr(prop.expr)
                setInt(buffer.byteCode.size, skipPos)
            }
        }
    }

    private val Sym.index: Int
        get() = buffer.symIndex(this)
}