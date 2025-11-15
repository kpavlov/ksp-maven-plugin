package me.kpavlov.ksp.maven.testprocessor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provider for HelloProcessor
 */
class HelloProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return HelloProcessor(environment.codeGenerator, environment.logger)
    }
}
