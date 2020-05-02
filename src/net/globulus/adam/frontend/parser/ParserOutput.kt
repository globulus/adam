package net.globulus.adam.frontend.parser

import net.globulus.adam.api.Expr
import net.globulus.adam.api.Scope

class ParserOutput(val rootScope: Scope, val exprs: List<Expr>)