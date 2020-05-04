package net.globulus.kadam

enum class KadamOpCode {
    PUSH_SYM,
    PUSH_INT,
    PUSH_FLOAT,
    PUSH_STR,
    LOAD,
    STORE_VAR,
    ADD,
    PRINT,
    ;

    val byte = ordinal.toByte()

    companion object {
        fun from(byte: Byte) = values()[byte.toInt()]
    }
}

fun MutableList<Byte>.put(opCode: KadamOpCode) {
    add(opCode.byte)
}