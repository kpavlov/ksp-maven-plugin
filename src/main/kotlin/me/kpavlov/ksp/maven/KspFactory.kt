package me.kpavlov.ksp.maven

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Factory for creating [KotlinSymbolProcessing] instances.
 *
 * This interface allows for customization of the KSP processing pipeline,
 * primarily useful for testing purposes.
 */
interface KspFactory {
    /**
     * Creates a new [KotlinSymbolProcessing] instance.
     *
     * @param kspConfig the KSP configuration
     * @param symbolProcessorProviders the list of symbol processor providers
     * @param logger the KSP logger
     * @return a new KotlinSymbolProcessing instance
     */
    fun create(
        kspConfig: KSPConfig,
        symbolProcessorProviders: List<SymbolProcessorProvider>,
        logger: KspLogger,
    ): KotlinSymbolProcessing
}

/**
 * Default implementation of [KspFactory].
 */
object DefaultKspFactory : KspFactory {
    override fun create(
        kspConfig: KSPConfig,
        symbolProcessorProviders: List<SymbolProcessorProvider>,
        logger: KspLogger,
    ) = KotlinSymbolProcessing(
        kspConfig = kspConfig,
        symbolProcessorProviders = symbolProcessorProviders,
        logger = logger,
    )
}
