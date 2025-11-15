package me.kpavlov.ksp.maven

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.NonExistLocation
import org.apache.maven.plugin.logging.Log

class KspLogger(
    val log: Log,
) : KSPLogger {
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
        log.error("[ksp] ${decorateMessage(message, symbol)}")
    }

    override fun exception(e: Throwable) {
        log.error("[ksp] ${e.message}", e)
    }

    override fun info(
        message: String,
        symbol: KSNode?,
    ) {
        log.info("[ksp] ${decorateMessage(message, symbol)}")
    }

    override fun logging(
        message: String,
        symbol: KSNode?,
    ) {
        log.info("[ksp] ${decorateMessage(message, symbol)}")
    }

    override fun warn(
        message: String,
        symbol: KSNode?,
    ) {
        log.warn("[ksp] ${decorateMessage(message, symbol)}")
    }
}
