package net.globulus.adam.api

/**
 * An [Expr] contains a BoobyTrap when it shouldn't be executed unless the middle-end of an
 * Adam implementation can defuse it safely. The typical use case is when an Expr uses a Symbol that
 * hasn't been defined yet, but the front-end can't really know if it's been defined or not as it
 * doesn't have insight into how does the middle-end implement variable declaration. Hence, instead of
 * throwing an Exception, we place the BoobyTrap inside the expr and have middleware unpack it.
 */
internal interface BoobyTrap {
    val defusal: Defusal
    val exception: Exception
}

/**
 * Defines an action of asserting if a certain [BoobyTrap] should be triggered or not.
 * E.g, a BT wrapping an [UndefinedSymException] will have its assertion look up the scope
 * for the Sym and check its type.
 */
internal interface Defusal {
    /**
     * @return true if BT should be defused
     */
    fun defuse(scope: Scope): Boolean
}