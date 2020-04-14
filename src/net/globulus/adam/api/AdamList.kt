package net.globulus.adam.api

interface AdamList : Value {
}

class StructList : AdamList, Type

class RawList : AdamList

class ArgList : AdamList

class GenList : AdamList