package net.globulus.adam.api

interface Type

class Blockdef(val gens: GenList?,
               val rec: Sym?,
               val args: StructList?,
               val ret: Type
) : Type

