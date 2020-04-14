package net.globulus.adam.frontend.parser

import net.globulus.adam.frontend.Token

class ParseException(token: Token,
                     message: String
) : Exception("Parse exception @$token: $message.")