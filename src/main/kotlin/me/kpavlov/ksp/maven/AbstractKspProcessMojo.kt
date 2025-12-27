package me.kpavlov.ksp.maven

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.descriptor.PluginDescriptor
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.ServiceLoader
import java.util.jar.JarFile

@Suppress("TooManyFunctions")
abstract class AbstractKspProcessMojo : AbstractMojo() {
    companion object {
        private const val KSP_SERVICE_FILE =
            "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider"
    }

    /**
     * Processing scope (MAIN or TEST) - must be initialized by subclasses
     */
    protected abstract val scope: ProcessingScope

    /**
     * Factory for creating KSP instances. Can be overridden for testing.
     * Each invocation creates a new isolated instance.
     */
    protected open val kspFactory: KspFactory = DefaultKspFactory

    @Parameter(defaultValue = "\${plugin}", readonly = true)
    private var pluginDescriptor: PluginDescriptor? = null

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    protected lateinit var project: MavenProject

    /**
     * Source directory to process
     */
    @Parameter
    private var sourceDirectory: File? = null

    /**
     * Additional source directories
     */
    @Parameter
    private val sourceDirs: MutableList<File>? = null

    /**
     * Output directory for generated Kotlin sources
     */
    @Parameter
    private var kotlinOutputDir: File? = null

    /**
     * Output directory for generated Java sources
     */
    @Parameter
    private var javaOutputDir: File? = null

    /**
     * Output directory for compiled classes
     */
    @Parameter
    private var classOutputDir: File? = null

    /**
     * Output directory for resources
     */
    @Parameter
    private var resourceOutputDir: File? = null

    /**
     * KSP output directory
     */
    @Parameter
    private var kspOutputDir: File? = null

    /**
     * Cache directory for incremental processing
     */
    @Parameter
    private var cachesDir: File? = null

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
    protected val debug = false

    /**
     * KSP processor options (key-value pairs)
     */
    @Parameter
    private val apOptions: MutableMap<String, String> = mutableMapOf()

    /**
     * Module name
     */
    @Parameter(defaultValue = "\${project.artifactId}")
    private lateinit var moduleName: String

    /**
     * JVM target version
     */
    @Parameter(defaultValue = "11")
    private lateinit var jvmTarget: String

    /**
     * Add generated sources to compilation
     */
    @Parameter(defaultValue = "true")
    private val addGeneratedSourcesToCompile = true

    /**
     * Returns true if processing should be skipped.
     */
    protected abstract fun isSkip(): Boolean

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        if (isSkip()) {
            log.info("Skipping KSP processing ($scope scope)")
            return
        }

        val actualSourceDirectory = getActualSourceDirectory()
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

        if (addGeneratedSourcesToCompile) {
            addGeneratedSources()
        }
    }

    private fun getActualSourceDirectory(): File =
        sourceDirectory ?: scope.getSourceDirectory(project)

    private fun getActualKotlinOutputDir(): File =
        kotlinOutputDir ?: scope.getDefaultKotlinOutputDir(project.build.directory)

    private fun getActualJavaOutputDir(): File =
        javaOutputDir ?: scope.getDefaultJavaOutputDir(project.build.directory)

    private fun getActualClassOutputDir(): File =
        classOutputDir ?: scope.getDefaultClassOutputDir(project.build.directory)

    private fun getActualResourceOutputDir(): File =
        resourceOutputDir ?: scope.getDefaultResourceOutputDir(project.build.directory)

    private fun getActualKspOutputDir(): File =
        kspOutputDir ?: scope.getDefaultKspOutputDir(project.build.directory)

    private fun getActualCachesDir(): File =
        cachesDir ?: scope.getDefaultCachesDir(project.build.directory)

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
        val providers =
            ServiceLoader
                .load(
                    SymbolProcessorProvider::class.java,
                    classLoader,
                ).toList()

        if (debug && providers.isNotEmpty()) {
            log.info("Processor providers: ${providers.map { it::class.qualifiedName }}")
        }

        return providers
    }

    private fun createKspConfig(): KSPJvmConfig {
        val config =
            KSPJvmConfig(
                javaSourceRoots = sourceDirs ?: emptyList(),
                javaOutputDir = getActualJavaOutputDir(),
                jdkHome = File(System.getProperty("java.home")),
                jvmTarget = jvmTarget,
                jvmDefaultMode = jvmDefaultMode,
                moduleName = moduleName,
                sourceRoots = listOf(getActualSourceDirectory()),
                commonSourceRoots = emptyList(),
                libraries = scope.getClasspathElements(project).map(::File),
                friends = emptyList(),
                processorOptions = apOptions,
                projectBaseDir = project.basedir,
                outputBaseDir = File(project.build.outputDirectory),
                cachesDir = getActualCachesDir(),
                classOutputDir = getActualClassOutputDir(),
                kotlinOutputDir = getActualKotlinOutputDir(),
                resourceOutputDir = getActualResourceOutputDir(),
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
                    ToStringBuilder.reflectionToString(config, ToStringStyle.MULTI_LINE_STYLE)
                }",
            )
        }

        return config
    }

    private fun executeProcessing(
        kspConfig: KSPJvmConfig,
        processorProviders: List<SymbolProcessorProvider>,
    ) {
        // Create a new KotlinSymbolProcessing instance for each execution
        // to ensure complete isolation in parallel builds
        val processing =
            try {
                kspFactory.create(
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
        val directories =
            listOf(
                getActualKotlinOutputDir(),
                getActualJavaOutputDir(),
                getActualClassOutputDir(),
                getActualResourceOutputDir(),
                getActualKspOutputDir(),
                getActualCachesDir(),
            )

        try {
            directories.forEach { Files.createDirectories(it.toPath()) }
        } catch (e: IOException) {
            throw MojoExecutionException("Failed to create output directories", e)
        }
    }

    private fun findKspProcessors(): List<File> {
        val pluginProcessors =
            pluginDescriptor?.artifacts?.let { findProcessorsInArtifacts(it) } ?: emptyList()
        val projectProcessors = findProcessorsInArtifacts(project.artifacts)

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
        val kotlinOutputDir = getActualKotlinOutputDir()
        if (!kotlinOutputDir.exists()) return

        if (debug) {
            log.info("Adding generated Kotlin sources: $kotlinOutputDir")
        }

        scope.addCompileSourceRoot(project, kotlinOutputDir.absolutePath)
    }

    private fun addJavaSources() {
        val javaOutputDir = getActualJavaOutputDir()
        val kotlinOutputDir = getActualKotlinOutputDir()

        if (!javaOutputDir.exists() || javaOutputDir == kotlinOutputDir) return

        if (debug) {
            log.info("Adding generated Java sources: $javaOutputDir")
        }

        scope.addCompileSourceRoot(project, javaOutputDir.absolutePath)
    }

    private fun addResources() {
        val resourceOutputDir = getActualResourceOutputDir()
        if (!resourceOutputDir.exists()) return

        if (debug) {
            log.info("Adding generated resources: $resourceOutputDir")
        }

        val resource =
            org.apache.maven.model.Resource().apply {
                directory = resourceOutputDir.absolutePath
            }

        scope.addResource(project, resource)
    }
}
