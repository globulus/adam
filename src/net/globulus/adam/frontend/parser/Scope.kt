package net.globulus.adam.frontend.parser

import net.globulus.adam.api.Sym
import net.globulus.adam.api.Type
import java.util.*

class Scope(val parent: Scope?) {
    private val guid = UUID.randomUUID().toString()
    val syms = mutableSetOf<Sym>()
    val types = mutableSetOf<Type>()
    val typeAliases = mutableMapOf<Sym, Type>()
    val childScopes = mutableListOf<Scope>()

    init {
        parent?.childScopes?.add(this)
    }

    override fun equals(other: Any?): Boolean {
        return guid == (other as? Scope)?.guid
    }

    override fun hashCode(): Int {
        return guid.hashCode()
    }
}