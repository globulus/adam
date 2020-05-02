package net.globulus.adam.frontend.compiler

enum class OpCode {
    DEF, // Followed by sym index and DEF_ OpSpecifier, tells that a certain symbol should be defined
    LOAD,
    GET, // Followed by a sym index, tells that the value on stack should be invoked with getter to specified Sym
    CONST, // Followed by a CONST_ OpSpecifier, tells that the const should be put on the stack
    CALL, // Tells that the value on stack should be invoked with the following arg list
    ARGS,
    HALT,
    ;

    val byte = ordinal.toByte()

    companion object {
        fun from(byte: Byte) = values()[byte.toInt()]
    }
}

enum class OpSpecifier {
    DEF_NUM,
    DEF_STR,
    DEF_ELSE,
    DEF_STRUCT_LIST,
    CONST_INT, // The constant to be loaded is a Long
    CONST_FLOAT, // The constant to be loaded is a Double
    CONST_STR, // The constant to be loaded is a String (i.e its StrTable index)
    ;

    val byte = ordinal.toByte()

    companion object {
        fun from(byte: Byte) = OpSpecifier.values()[byte.toInt()]
    }
}