package me.kpavlov.ksp.maven

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.maven.model.Resource
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.*
import java.util.jar.JarFile

/**
 * Mojo for running Kotlin Symbol Processing (KSP) on JVM sources.
 *
 * This plugin discovers KSP processors from project dependencies and executes them
 * to generate source code, resources, and other artifacts.
 *
 * @author Konstantin Pavlov
 */
@Mojo(
    name = "process",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true,
)
class KspProcessMojo(
    private val kspFactory: KspFactory = DefaultKspFactory,
) : AbstractMojo() {

    companion object {
        private const val KSP_SERVICE_FILE =
            "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider"
    }

    @Parameter(defaultValue = $$"${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    /**
     * Source directories to process
     */
    @Parameter(defaultValue = $$"${project.build.sourceDirectory}")
    private lateinit var sourceDirectory: File

    /**
     * Additional source directories
     */
    @Parameter
    private val sourceDirs: MutableList<File>? = null

    /**
     * Output directory for generated Kotlin sources
     */
    @Parameter(defaultValue = $$"${project.build.directory}/generated-sources/ksp")
    private lateinit var kotlinOutputDir: File

    /**
     * Output directory for generated Java sources
     */
    @Parameter(defaultValue = $$"${project.build.directory}/generated-sources/ksp")
    private lateinit var javaOutputDir: File

    /**
     * Output directory for compiled classes
     */
    @Parameter(defaultValue = $$"${project.build.directory}/ksp-classes")
    private lateinit var classOutputDir: File

    /**
     * Output directory for resources
     */
    @Parameter(defaultValue = $$"${project.build.directory}/generated-resources/ksp")
    private lateinit var resourceOutputDir: File

    /**
     * KSP output directory
     */
    @Parameter(defaultValue = $$"${project.build.directory}/ksp")
    private lateinit var kspOutputDir: File

    /**
     * Cache directory for incremental processing
     */
    @Parameter(defaultValue = $$"${project.build.directory}/ksp-cache")
    private lateinit var cachesDir: File

    /**
     * Enable incremental processing
     */
    @Parameter(defaultValue = "false")
    private val incremental: Boolean = false

    @Parameter(defaultValue = "false")
    private val incrementalLog: Boolean = false

    @Parameter(defaultValue = "disable")
    private var jvmDefaultMode: String = "disable"

    @Parameter(defaultValue = "2.2")
    private lateinit var languageVersion: String

    @Parameter(defaultValue = "2.2")
    private lateinit var apiVersion: String

    /**
     * Indicates whether to ignore processing errors during the KSP (Kotlin Symbol Processing) execution.
     *
     * When set to `true`, the processing will continue even if errors occur, potentially generating
     * incomplete or invalid outputs. If set to `false`, the execution will halt on encountering an error.
     *
     * This setting is disabled (`false`) by default.
     */
    @Parameter(defaultValue = "false")
    private val ignoreProcessingErrors = false

    @Parameter(defaultValue = "false")
    private val allWarningsAsErrors = false

    @Parameter(defaultValue = "true")
    private val mapAnnotationArgumentsInJava: Boolean = true

    /**
     * Enable debug output
     */
    @Parameter(defaultValue = "false")
    private val debug = false

    /**
     * KSP processor options (key-value pairs)
     */
    @Parameter
    private val apOptions: MutableMap<String, String> = mutableMapOf()

    /**
     * Module name
     */
    @Parameter(defaultValue = $$"${project.artifactId}")
    private lateinit var moduleName: String

    /**
     * JVM target version
     */
    @Parameter(defaultValue = "11")
    private lateinit var jvmTarget: String

    /**
     * Skip KSP processing
     */
    @Parameter(property = "ksp.skip", defaultValue = "false")
    private val skip = false

    /**
     * Add generated sources to compilation
     */
    @Parameter(defaultValue = "true")
    private val addGeneratedSourcesToCompile = true

    private val kspLogger = KspLogger(log = log)

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        if (skip) {
            log.info("Skipping KSP processing")
            return
        }

        createDirectories()

        val processorJars = findKspProcessors()
        if (processorJars.isEmpty()) {
            log.info("No KSP processors found in dependencies, skipping KSP processing")
            return
        }

        logProcessorsFound(processorJars)

        executeKsp(processorJars)

        if (addGeneratedSourcesToCompile) {
            addGeneratedSources()
        }
    }

    private fun logProcessorsFound(processorJars: List<File>) {
        log.info("Found ${processorJars.size} KSP processor(s)")
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

    private fun executeKsp(processorJars: List<File>) {
        val processorClassloader = createProcessorClassLoader(processorJars)
        val processorProviders = loadProcessorProviders()
        val kspConfig = createKspConfig()

        executeProcessing(kspConfig, processorProviders)
    }

    private fun createProcessorClassLoader(processorJars: List<File>): URLClassLoader {
        if (debug) {
            log.info("Preparing processor Classloader")
        }

        val classLoader = URLClassLoader(processorJars.map { it.toURI().toURL() }.toTypedArray())

        if (debug) {
            log.info("Processor Classloader: $classLoader")
        }

        return classLoader
    }

    private fun loadProcessorProviders(): List<SymbolProcessorProvider> {
        val providers = ServiceLoader.load(SymbolProcessorProvider::class.java).toList()

        if (debug && providers.isNotEmpty()) {
            log.info("Processor providers: $providers")
        }

        return providers
    }

    private fun createKspConfig(): KSPJvmConfig {
        val config =
            KSPJvmConfig(
                javaSourceRoots = sourceDirs ?: emptyList(),
                javaOutputDir = javaOutputDir,
                jdkHome = File(System.getProperty("java.home")),
                jvmTarget = jvmTarget,
                jvmDefaultMode = jvmDefaultMode,
                moduleName = moduleName,
                sourceRoots = listOf(sourceDirectory),
                commonSourceRoots = emptyList(),
                libraries = project.compileClasspathElements.map(::File),
                friends = emptyList(),
                processorOptions = apOptions,
                projectBaseDir = project.basedir,
                outputBaseDir = File(project.build.outputDirectory),
                cachesDir = cachesDir,
                classOutputDir = classOutputDir,
                kotlinOutputDir = kotlinOutputDir,
                resourceOutputDir = resourceOutputDir,
                incremental = incremental,
                incrementalLog = incrementalLog,
                modifiedSources = mutableListOf(),
                removedSources = mutableListOf(),
                changedClasses = mutableListOf(),
                languageVersion = languageVersion,
                apiVersion = apiVersion,
                allWarningsAsErrors = allWarningsAsErrors,
                mapAnnotationArgumentsInJava = mapAnnotationArgumentsInJava,
            )

        if (debug) {
            log.info(
                "Calling KSP processing with config: ${
                    ToStringBuilder.reflectionToString(
                        config
                    )
                }"
            )
        }

        return config
    }

    private fun executeProcessing(
        kspConfig: KSPJvmConfig,
        processorProviders: List<SymbolProcessorProvider>,
    ) {
        try {
            val processing =
                kspFactory.create(
                    kspConfig = kspConfig,
                    symbolProcessorProviders = processorProviders,
                    logger = kspLogger,
                )

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

    private fun logIncrementalChanges(config: KSPJvmConfig) {
        if (!debug) return

        if (config.modifiedSources.isNotEmpty()) {
            log.info("KSP processing finished, modifiedSources: ${config.modifiedSources}")
        }

        if (config.removedSources.isNotEmpty()) {
            log.info("KSP processing finished, removedSources: ${config.removedSources}")
        }

        if (config.changedClasses.isNotEmpty()) {
            log.info("KSP processing finished, changedClasses: ${config.changedClasses}")
        }
    }

    private fun handleExecutionResult(exitCode: KotlinSymbolProcessing.ExitCode) {
        if (exitCode == KotlinSymbolProcessing.ExitCode.OK) return

        log.error("KotlinSymbolProcessing failed with exit code: $exitCode")

        if (ignoreProcessingErrors) {
            log.warn("Continue build because `ignoreProcessingErrors` is true")
        } else {
            throw MojoFailureException(
                "KotlinSymbolProcessing failed with exit code: $exitCode",
            )
        }
    }

    private fun createDirectories() {
        val directories = listOf(
            kotlinOutputDir,
            javaOutputDir,
            classOutputDir,
            resourceOutputDir,
            kspOutputDir,
            cachesDir
        )

        try {
            directories.forEach { Files.createDirectories(it.toPath()) }
        } catch (e: IOException) {
            throw MojoExecutionException("Failed to create output directories", e)
        }
    }

    private fun findKspProcessors(): List<File> =
        project.artifacts
            .mapNotNull { artifact ->
                artifact.file?.takeIf { it.exists() && isKspProcessor(it) }?.also {
                    log.debug("Found KSP processor: ${artifact.artifactId}")
                }
            }

    private fun isKspProcessor(jar: File): Boolean =
        runCatching {
            JarFile(jar).use { jarFile ->
                jarFile.getJarEntry(KSP_SERVICE_FILE) != null
            }
        }.getOrDefault(false)

    private fun addGeneratedSources() {
        addKotlinSources()
        addJavaSources()
        addResources()
    }

    private fun addKotlinSources() {
        if (!kotlinOutputDir.exists()) return

        if (debug) {
            log.info("Adding generated Kotlin sources: $kotlinOutputDir")
        }
        project.addCompileSourceRoot(kotlinOutputDir.absolutePath)
    }

    private fun addJavaSources() {
        if (!javaOutputDir.exists() || javaOutputDir == kotlinOutputDir) return

        if (debug) {
            log.info("Adding generated Java sources: $javaOutputDir")
        }
        project.addCompileSourceRoot(javaOutputDir.absolutePath)
    }

    private fun addResources() {
        if (!resourceOutputDir.exists()) return

        if (debug) {
            log.info("Adding generated resources: $resourceOutputDir")
        }

        project.addResource(
            Resource().apply {
                directory = resourceOutputDir.absolutePath
            },
        )
    }
}
