package net.globulus.adam.api

interface AdamList : Value {
}

class StructList(val props: List<Prop>) : AdamList, Type {
    class Prop(val type: Type, val sym: Sym, val expr: Expr?)
}

open class RawList(val props: List<Prop>) : AdamList {
    class Prop(val sym: Sym?, val expr: Expr)
}

class ArgList(props: List<Prop>) : RawList(props)

class GenList(val props: List<Prop>) : AdamList {
    class Prop(val type: Type?, val sym: Sym)
}