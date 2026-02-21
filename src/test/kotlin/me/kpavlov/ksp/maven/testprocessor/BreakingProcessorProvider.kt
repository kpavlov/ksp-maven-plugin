package me.kpavlov.ksp.maven.testprocessor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * A deliberately destructive KSP processor used to verify `processorExcludes` filtering.
 *
 * When active, [BreakingProcessor] generates a Kotlin file containing invalid syntax,
 * causing a hard compilation failure. The sample project excludes this provider via
 * `<processorExcludes>` to prove that the exclude filter works: if exclusion is broken
 * and this processor runs, the build fails with a compile error.
 */
class BreakingProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        BreakingProcessor(environment.codeGenerator, environment.logger)
}
