package me.kpavlov.ksp.maven

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Resource
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.codehaus.plexus.util.xml.Xpp3Dom
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.ServiceLoader
import java.util.jar.JarFile

/**
 * Executes KSP (Kotlin Symbol Processing) for Maven Mojos.
 *
 * This class contains all the core KSP processing logic and is invoked from Java Mojos
 * via the [KspMojoExecutors] facade. The Java Mojos hold the Maven parameter annotations
 * with Javadoc for the maven-plugin-plugin to extract documentation, while this class
 * handles the actual processing.
 *
 * Thread Safety: This executor creates isolated KSP instances for each execution,
 * making it safe for parallel Maven builds.
 */
internal class KspMojoExecutor(
    private val params: KspMojoParameters,
) {
    companion object {
        private const val KSP_SERVICE_FILE =
            "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider"
    }

    private val log get() = params.log
    private val scope get() = params.scope
    private val debug get() = params.debug

    /**
     * Executes KSP processing.
     *
     * @throws MojoExecutionException if KSP execution fails
     * @throws MojoFailureException if KSP processing reports errors
     */
    @Throws(MojoExecutionException::class, MojoFailureException::class)
    fun execute() {
        val actualSourceDirectory = params.getActualSourceDirectory()
        if (!actualSourceDirectory.exists()) {
            log.info(
                "Source directory does not exist, skipping KSP processing: $actualSourceDirectory",
            )
            return
        }

        createDirectories()

        val processorJars = findKspProcessors()
        if (processorJars.isEmpty()) {
            log.info("No KSP processors found in dependencies, skipping KSP processing")
            return
        }

        logProcessorsFound(processorJars)

        executeKsp()

        if (params.addGeneratedSourcesToCompile) {
            addGeneratedSources()
        }
    }

    private fun logProcessorsFound(processorJars: List<File>) {
        log.info("Found ${processorJars.size} KSP processor(s) for $scope sources")
        if (debug) {
            val processorList =
                processorJars.joinToString(
                    prefix = " - ",
                    separator = "\n - ",
                    transform = File::getName,
                )
            log.info("${processorJars.size} KSP processor(s):\n$processorList")
        }
    }

    private fun executeKsp() {
        val processorProviders = loadProcessorProviders()
        val kspConfig = createKspConfig()

        executeProcessing(kspConfig, processorProviders)
    }

    private fun loadProcessorProviders(): List<SymbolProcessorProvider> {
        // Create a new ServiceLoader instance for each execution to ensure thread safety
        // and proper isolation in parallel builds
        val classLoader = Thread.currentThread().contextClassLoader
        val discovered =
            ServiceLoader
                .load(
                    SymbolProcessorProvider::class.java,
                    classLoader,
                ).toList()

        if (debug) {
            log.info(
                "Discovered ${discovered.size} provider(s): " +
                    discovered.map { it::class.qualifiedName },
            )
            if (params.processorIncludes.isNotEmpty()) {
                log.info("processorIncludes: ${params.processorIncludes}")
            }
            if (params.processorExcludes.isNotEmpty()) {
                log.info("processorExcludes: ${params.processorExcludes}")
            }
        }

        val providers =
            filterProcessorProviders(
                providers = discovered,
                includes = params.processorIncludes,
                excludes = params.processorExcludes,
                log = log,
            )

        if (discovered.size != providers.size) {
            log.info(
                "KSP processor filtering: ${discovered.size} discovered, " +
                    "${providers.size} active after applying includes/excludes filters",
            )
        }

        if (debug) {
            log.info("Active processor providers: ${providers.map { it::class.qualifiedName }}")
        }

        return providers
    }

    private fun createKspConfig(): KSPJvmConfig {
        val project = params.project
        val kotlinConfig =
            project.build
                ?.pluginsAsMap
                ?.get(
                    "org.jetbrains.kotlin:kotlin-maven-plugin",
                )?.configuration as? Xpp3Dom

        val resolvedLanguageVersion =
            params.languageVersion
                ?: kotlinConfig?.getChild("languageVersion")?.value
                ?: project.properties.getProperty("kotlin.compiler.languageVersion")
                ?: project.properties.getProperty("kotlin.version")?.let { version ->
                    val parts = version.split(".")
                    if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
                } ?: "2.2"

        val resolvedApiVersion =
            params.apiVersion
                ?: kotlinConfig?.getChild("apiVersion")?.value
                ?: project.properties.getProperty("kotlin.compiler.apiVersion")
                ?: resolvedLanguageVersion

        val resolvedJvmTarget =
            params.jvmTarget
                ?: kotlinConfig?.getChild("jvmTarget")?.value
                ?: project.properties.getProperty("kotlin.compiler.jvmTarget")
                ?: project.properties.getProperty("maven.compiler.release")
                ?: project.properties.getProperty("maven.compiler.target")
                ?: "11"

        val resolvedJdkHome =
            kotlinConfig?.getChild("jdkHome")?.value
                ?: project.properties.getProperty("kotlin.compiler.jdkHome")
                ?: System.getProperty("java.home")

        val resolvedAllWarningsAsErrors =
            params.allWarningsAsErrors ||
                kotlinConfig?.getChild("allWarningsAsErrors")?.value?.toBoolean() == true ||
                project.properties
                    .getProperty(
                        "kotlin.compiler.allWarningsAsErrors",
                    )?.toBoolean() == true

        val kspJvmConfig =
            KSPJvmConfig(
                javaSourceRoots = params.sourceDirs,
                javaOutputDir = params.getActualJavaOutputDir(),
                jdkHome = File(resolvedJdkHome),
                jvmTarget = resolvedJvmTarget,
                jvmDefaultMode = params.jvmDefaultMode,
                moduleName = params.moduleName,
                sourceRoots = listOf(params.getActualSourceDirectory()),
                commonSourceRoots = emptyList(),
                libraries = scope.getClasspathElements(project).map(::File),
                friends = emptyList(),
                processorOptions = params.processorOptions,
                projectBaseDir = project.basedir,
                outputBaseDir = File(project.build.outputDirectory),
                cachesDir = params.getActualCachesDir(),
                classOutputDir = params.getActualClassOutputDir(),
                kotlinOutputDir = params.getActualKotlinOutputDir(),
                resourceOutputDir = params.getActualResourceOutputDir(),
                incremental = params.incremental,
                incrementalLog = params.incrementalLog,
                modifiedSources = mutableListOf(),
                removedSources = mutableListOf(),
                changedClasses = mutableListOf(),
                languageVersion = resolvedLanguageVersion,
                apiVersion = resolvedApiVersion,
                allWarningsAsErrors = resolvedAllWarningsAsErrors,
                mapAnnotationArgumentsInJava = params.mapAnnotationArgumentsInJava,
            )

        if (debug) {
            log.info(
                "Calling KSP processing with config: ${
                    ToStringBuilder.reflectionToString(kspJvmConfig, ToStringStyle.MULTI_LINE_STYLE)
                }",
            )
        }

        return kspJvmConfig
    }

    private fun executeProcessing(
        kspConfig: KSPJvmConfig,
        processorProviders: List<SymbolProcessorProvider>,
    ) {
        // Create a new KotlinSymbolProcessing instance for each execution
        // to ensure complete isolation in parallel builds
        val processing =
            try {
                params.kspFactory.create(
                    kspConfig = kspConfig,
                    symbolProcessorProviders = processorProviders,
                    logger = KspLogger(log = log, scope = scope),
                )
            } catch (ex: Exception) {
                log.error("Failed to create KotlinSymbolProcessing instance", ex)
                throw MojoExecutionException(
                    "Failed to create KotlinSymbolProcessing instance: ${ex.message}",
                    ex,
                )
            }

        try {
            val exitCode = processing.execute()
            logIncrementalChanges(kspConfig)
            handleExecutionResult(exitCode)
        } catch (ex: MojoFailureException) {
            throw ex
        } catch (ex: Exception) {
            log.error("Failed to execute KotlinSymbolProcessing", ex)
            throw MojoExecutionException(
                "Failed to execute KotlinSymbolProcessing: ${ex.message}",
                ex,
            )
        }
    }

    private fun logIncrementalChanges(kspJvmConfig: KSPJvmConfig) {
        if (!debug) return

        if (kspJvmConfig.modifiedSources.isNotEmpty()) {
            log.info("KSP processing finished, modifiedSources: ${kspJvmConfig.modifiedSources}")
        }

        if (kspJvmConfig.removedSources.isNotEmpty()) {
            log.info("KSP processing finished, removedSources: ${kspJvmConfig.removedSources}")
        }

        if (kspJvmConfig.changedClasses.isNotEmpty()) {
            log.info("KSP processing finished, changedClasses: ${kspJvmConfig.changedClasses}")
        }
    }

    private fun handleExecutionResult(exitCode: KotlinSymbolProcessing.ExitCode) {
        if (exitCode == KotlinSymbolProcessing.ExitCode.OK) return

        log.error("KotlinSymbolProcessing failed with exit code: $exitCode")

        if (params.ignoreProcessingErrors) {
            log.warn("Continue build because `ignoreProcessingErrors` is true")
        } else {
            throw MojoFailureException(
                "KotlinSymbolProcessing failed with exit code: $exitCode",
            )
        }
    }

    private fun createDirectories() {
        val directories =
            listOf(
                params.getActualKotlinOutputDir(),
                params.getActualJavaOutputDir(),
                params.getActualClassOutputDir(),
                params.getActualResourceOutputDir(),
                params.getActualKspOutputDir(),
                params.getActualCachesDir(),
            )

        try {
            directories.forEach { Files.createDirectories(it.toPath()) }
        } catch (e: IOException) {
            throw MojoExecutionException("Failed to create output directories", e)
        }
    }

    private fun findKspProcessors(): List<File> {
        val pluginArtifacts = params.pluginDescriptor?.artifacts
        val projectArtifacts = params.project.artifacts
        val pluginProcessors = pluginArtifacts?.let { findProcessorsInArtifacts(it) } ?: emptyList()
        val projectProcessors = findProcessorsInArtifacts(projectArtifacts)

        return pluginProcessors + projectProcessors
    }

    private fun findProcessorsInArtifacts(artifacts: Collection<Artifact>): List<File> =
        artifacts.mapNotNull { artifact ->
            artifact.file?.takeIf { it.exists() && isKspProcessor(it) }?.also {
                log.debug("Found KSP processor: ${artifact.artifactId}")
            }
        }

    private fun isKspProcessor(jar: File): Boolean =
        runCatching {
            JarFile(jar).use { it.getJarEntry(KSP_SERVICE_FILE) != null }
        }.getOrElse { ex ->
            log.warn("Could not inspect JAR ${jar.name}: ${ex.message}")
            false
        }

    private fun addGeneratedSources() {
        addKotlinSources()
        addJavaSources()
        addResources()
    }

    private fun addKotlinSources() {
        val kotlinOutputDir = params.getActualKotlinOutputDir()
        if (!kotlinOutputDir.exists()) return

        if (debug) {
            log.info("Adding generated Kotlin sources: $kotlinOutputDir")
        }

        scope.addCompileSourceRoot(params.project, kotlinOutputDir.absolutePath)
    }

    private fun addJavaSources() {
        val javaOutputDir = params.getActualJavaOutputDir()
        val kotlinOutputDir = params.getActualKotlinOutputDir()

        if (!javaOutputDir.exists() || javaOutputDir == kotlinOutputDir) return

        if (debug) {
            log.info("Adding generated Java sources: $javaOutputDir")
        }

        scope.addCompileSourceRoot(params.project, javaOutputDir.absolutePath)
    }

    private fun addResources() {
        val resourceOutputDir = params.getActualResourceOutputDir()
        if (!resourceOutputDir.exists()) return

        if (debug) {
            log.info("Adding generated resources: $resourceOutputDir")
        }

        val resource =
            Resource().apply {
                directory = resourceOutputDir.absolutePath
            }

        scope.addResource(params.project, resource)
    }
}
