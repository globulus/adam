package net.globulus.adam.frontend.compiler

enum class OpCode {
    DEF, // Followed by type def and sym index, tells that a certain symbol should be defined

    TYPE_BLOCK,
    TYPE_BLOCK_WITH_REC,
    TYPE_BLOCK_WITH_GENS,
    TYPE_BLOCK_WITH_GENS_AND_REC,
    TYPE_LIST,
    TYPE_LIST_WITH_GENS,
    TYPE_VARARG,
    TYPE_OPTIONAL,

    PUSH_SCOPE,
    POP_SCOPE,

    SYM,
    GET, // Followed by a sym index, tells that the value on stack should be invoked with getter to specified Sym

    // Followed by a CONST_ OpSpecifier, tells that the const should be put on the stack
    CONST_INT, // The constant to be loaded is a Long
    CONST_FLOAT, // The constant to be loaded is a Double
    CONST_STR, // The constant to be loaded is a String (i.e its StrTable index)

    BLOCK,

    CALL, // Tells that the value on stack should be invoked with the following arg list of size nextInt

    ARG_EXPR, // Denotes that this is followed by an arg Expr
    HALT,
    ;

    val byte = ordinal.toByte()

    companion object {
        fun from(byte: Byte) = values()[byte.toInt()]
    }
}