package net.globulus.adam.frontend.parser

object ParserLog {
    private val logLevel = Level.VERBOSE

    fun log(level: Level, message: String) {
        if (level < logLevel) {
            return
        }
        println("$level> $message")
    }

    fun v(message: String) {
        log(Level.VERBOSE, message)
    }

    fun ds(message: String) {
        log(Level.DESUGAR, message)
    }

    enum class Level {
        VERBOSE,
        DESUGAR
    }
}