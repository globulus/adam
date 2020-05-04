package net.globulus.adam.frontend.compiler

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

fun MutableList<Byte>.put(byte: Byte) {
    add(byte)
}

fun MutableList<Byte>.put(opCode: OpCode) {
    add(opCode.byte)
}

fun MutableList<Byte>.put(byteArray: ByteArray) {
    for (b in byteArray) {
        add(b)
    }
}

fun Int.toByteArray(): ByteArray {
    return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()
}

fun MutableList<Byte>.putInt(i: Int) {
    put(i.toByteArray())
}

fun MutableList<Byte>.setInt(i: Int, pos: Int) {
    for ((idx, b) in i.toByteArray().withIndex()) {
        set(pos + idx, b)
    }
}

fun MutableList<Byte>.putLong(l: Long) {
    put(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(l).array())
}

fun MutableList<Byte>.putDouble(d: Double) {
    put(ByteBuffer.allocate(8).putDouble(d).array())
}

fun ByteArrayOutputStream.put(opCode: OpCode) {
    write(opCode.byte.toInt())
}

fun ByteArrayOutputStream.putInt(i: Int) {
    write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(i).array())
}

fun ByteArrayOutputStream.putLong(l: Long) {
    write(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(l).array())
}

fun ByteArrayOutputStream.putDouble(d: Double) {
    write(ByteBuffer.allocate(8).putDouble(d).array())
}