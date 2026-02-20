package me.kpavlov.ksp.maven

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.NonExistLocation
import org.apache.maven.plugin.logging.Log

/**
 * KSP logger adapter that wraps Maven's logging with scope-aware prefixes.
 *
 * @param log Maven logger instance
 * @param scope Processing scope (MAIN or TEST) for log message prefixing
 */
internal class KspLogger(
    val log: Log,
    scope: ProcessingScope,
) : KSPLogger {
    private val prefix = "[ksp:${scope.name.lowercase()}]"

    private fun decorateMessage(
        message: String,
        symbol: KSNode?,
    ): String =
        when (val location = symbol?.location) {
            is FileLocation -> "${location.filePath}:${location.lineNumber}: $message"
            is NonExistLocation, null -> message
        }

    override fun error(
        message: String,
        symbol: KSNode?,
    ) {
        log.error("$prefix ${decorateMessage(message, symbol)}")
    }

    override fun exception(e: Throwable) {
        log.error("$prefix ${e.message}", e)
    }

    override fun info(
        message: String,
        symbol: KSNode?,
    ) {
        log.info("$prefix ${decorateMessage(message, symbol)}")
    }

    override fun logging(
        message: String,
        symbol: KSNode?,
    ) {
        log.info("$prefix ${decorateMessage(message, symbol)}")
    }

    override fun warn(
        message: String,
        symbol: KSNode?,
    ) {
        log.warn("$prefix ${decorateMessage(message, symbol)}")
    }
}
