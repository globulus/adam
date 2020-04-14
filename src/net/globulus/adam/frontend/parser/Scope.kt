package net.globulus.adam.frontend.parser

import net.globulus.adam.api.Sym
import net.globulus.adam.api.Type

class Scope(val parent: Scope?) {
    val syms = mutableSetOf<Sym>()
    val types = mutableSetOf<Type>()
    val typeAliases = mutableMapOf<Sym, Type>()
}