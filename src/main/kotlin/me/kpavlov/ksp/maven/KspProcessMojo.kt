package me.kpavlov.ksp.maven

import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Resource
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.stream.Collectors

/**
 * Mojo for running Kotlin Symbol Processing (KSP) on JVM sources.
 *
 * @author Konstantin Pavlov
 */
@Mojo(
    name = "process",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true
)
class KspProcessMojo : AbstractMojo() {
    @Parameter(defaultValue = $$"${project}", readonly = true, required = true)
    private val project: MavenProject? = null

    /**
     * Source directories to process
     */
    @Parameter(defaultValue = $$"${project.build.sourceDirectory}")
    private val sourceDirectory: File? = null

    /**
     * Additional source directories
     */
    @Parameter
    private val sourceDirs: MutableList<File?>? = null

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
    @Parameter(defaultValue = "true")
    private val incremental = false

    /**
     * KSP processor options (key-value pairs)
     */
    @Parameter
    private val apOptions: MutableMap<String?, String?>? = null

    /**
     * Additional compiler arguments
     */
    @Parameter
    private val compilerArgs: MutableList<String>? = null

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
    private val addGeneratedSourcesToCompile = false

    /**
     * KSP version to use
     */
    @Parameter(defaultValue = "2.3.2")
    private lateinit var kspVersion: String

    /**
     * Kotlin version to use
     */
    @Parameter(defaultValue = "2.2.21")
    private lateinit var kotlinVersion: String

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        if (skip) {
            log.info("Skipping KSP processing")
            return
        }

        // Create output directories
        createDirectories()

        // Find KSP processors
        val processorJars = findKspProcessors()
        if (processorJars.isEmpty()) {
            log.info("No KSP processors found in dependencies, skipping KSP processing")
            return
        }

        log.info("Found " + processorJars.size + " KSP processor(s)")

        // Build command arguments
        val args = buildCompilerArgs(processorJars)

        // Execute kotlinc
        executeKotlinCompiler(args)

        // Add generated sources to project
        if (addGeneratedSourcesToCompile) {
            addGeneratedSources()
        }
    }

    @Throws(MojoExecutionException::class)
    private fun createDirectories() {
        try {
            Files.createDirectories(kotlinOutputDir.toPath())
            Files.createDirectories(javaOutputDir.toPath())
            Files.createDirectories(classOutputDir.toPath())
            Files.createDirectories(resourceOutputDir.toPath())
            Files.createDirectories(kspOutputDir.toPath())
            Files.createDirectories(cachesDir.toPath())
        } catch (e: IOException) {
            throw MojoExecutionException("Failed to create output directories", e)
        }
    }

    private fun findKspProcessors(): MutableList<File> {
        val processors: MutableList<File> = mutableListOf()

        for (artifact in project!!.artifacts) {
            val file = artifact.file
            if (file != null && file.exists() && isKspProcessor(file)) {
                processors.add(file)
                log.debug("Found KSP processor: " + artifact.artifactId)
            }
        }

        return processors
    }

    private fun isKspProcessor(jar: File): Boolean {
        // Check if the JAR contains META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider
        try {
            val jarFile = JarFile(jar)
            val entry = jarFile.getJarEntry(
                "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider"
            )
            jarFile.close()
            return entry != null
        } catch (e: IOException) {
            return false
        }
    }

    @Throws(MojoExecutionException::class)
    private fun buildCompilerArgs(processorJars: MutableList<File>): MutableList<String> {
        val args: MutableList<String> = mutableListOf()

        // Find KSP plugin JARs
        val kspPluginJar =
            findDependency("com.google.devtools.ksp", "symbol-processing-aa-embeddable")
        val kspApiJar = findDependency("com.google.devtools.ksp", "symbol-processing-api")

        if (kspPluginJar == null) {
            throw MojoExecutionException(
                "KSP plugin JAR not found. Add dependency: com.google.devtools.ksp:ssymbol-processing-aa-embeddable:$kspVersion"
            )
        }
        if (kspApiJar == null) {
            throw MojoExecutionException(
                "KSP API JAR not found. Add dependency: com.google.devtools.ksp:symbol-processing-api:$kspVersion"
            )
        }

        // Add KSP plugin
        args.add("-Xplugin=" + kspPluginJar.absolutePath)
        args.add("-Xplugin=" + kspApiJar.absolutePath)

        // Allow compilation with no source files
        args.add("-Xallow-no-source-files")

        // Module name
        args.add("-module-name")
        args.add(moduleName)

        // JVM target
        args.add("-jvm-target")
        args.add(jvmTarget)

        // Build plugin options
        val pluginPrefix = "plugin:$KSP_PLUGIN_ID:"

        // Processor classpath
        val apClasspath = processorJars.stream()
            .map { obj: File? -> obj!!.absolutePath }
            .collect(Collectors.joining(File.pathSeparator))
        args.add("-P")
        args.add(pluginPrefix + "apclasspath=" + apClasspath)

        // Project base directory
        args.add("-P")
        args.add(pluginPrefix + "projectBaseDir=" + project!!.basedir.absolutePath)

        // Output directories
        args.add("-P")
        args.add(pluginPrefix + "classOutputDir=" + classOutputDir.absolutePath)
        args.add("-P")
        args.add(pluginPrefix + "javaOutputDir=" + javaOutputDir.absolutePath)
        args.add("-P")
        args.add(pluginPrefix + "kotlinOutputDir=" + kotlinOutputDir.absolutePath)
        args.add("-P")
        args.add(pluginPrefix + "resourceOutputDir=" + resourceOutputDir.absolutePath)
        args.add("-P")
        args.add(pluginPrefix + "kspOutputDir=" + kspOutputDir.absolutePath)
        args.add("-P")
        args.add(pluginPrefix + "cachesDir=" + cachesDir.absolutePath)

        // Incremental processing
        args.add("-P")
        args.add(pluginPrefix + "incremental=" + incremental)

        // Processor options
        if (apOptions != null && !apOptions.isEmpty()) {
            for (entry in apOptions.entries) {
                args.add("-P")
                args.add(pluginPrefix + "apoption=" + entry.key + "=" + entry.value)
            }
        }

        // Classpath
        val classpath = this.classpath
        if (!classpath.isEmpty()) {
            args.add("-classpath")
            args.add(classpath)
        }

        // Additional compiler arguments
        if (compilerArgs != null) {
            args.addAll(compilerArgs)
        }

        // Source files
        val sources = collectSourceFiles()
        if (!sources.isEmpty()) {
            for (source in sources) {
                args.add(source.absolutePath)
            }
        }

        return args
    }

    private fun findDependency(groupId: String, artifactId: String): File? {
        for (artifact in project!!.artifacts) {
            if (artifact.groupId == groupId &&
                artifact.artifactId == artifactId
            ) {
                return artifact.file
            }
        }
        return null
    }

    private val classpath: String
        get() = project!!.artifacts.stream()
            .filter { a: Artifact? -> a!!.file != null && a.file.exists() }
            .map { a: Artifact? -> a!!.file.absolutePath }
            .collect(Collectors.joining(File.pathSeparator))

    @Throws(MojoExecutionException::class)
    private fun collectSourceFiles(): MutableList<File> {
        val sources: MutableList<File> = ArrayList()


        // Main source directory
        if (sourceDirectory != null && sourceDirectory.exists()) {
            collectKotlinFiles(sourceDirectory, sources)
        }

        // Additional source directories
        if (sourceDirs != null) {
            for (dir in sourceDirs) {
                if (dir != null && dir.exists()) {
                    collectKotlinFiles(dir, sources)
                }
            }
        }

        return sources
    }

    @Throws(MojoExecutionException::class)
    private fun collectKotlinFiles(dir: File, files: MutableList<File>) {
        try {
            Files.walk(dir.toPath())
                .filter { path: Path? -> Files.isRegularFile(path) }
                .filter { p: Path? -> p.toString().endsWith(".kt") }
                .forEach { p: Path? -> files.add(p!!.toFile()) }
        } catch (e: IOException) {
            throw MojoExecutionException("Failed to collect source files from $dir", e)
        }
    }

    @Throws(MojoExecutionException::class)
    private fun executeKotlinCompiler(args: MutableList<String>) {
        log.info("Running KSP processing...")

        if (log.isDebugEnabled) {
            log.debug("Compiler arguments:")
            for (arg in args) {
                log.debug("  $arg")
            }
        }

        try {
            // Use Kotlin compiler embeddable
            val compiler = K2JVMCompiler()


            // Convert arguments to array
            val argsArray = args.toTypedArray<String>()


            // Execute compilation
            val exitCode: ExitCode = compiler.exec(
                System.err,
                *argsArray
            )

            if (exitCode != ExitCode.OK) {
                throw MojoExecutionException("KSP processing failed with exit code: $exitCode")
            }

            log.info("KSP processing completed successfully")
        } catch (e: Exception) {
            throw MojoExecutionException("Failed to execute KSP processing", e)
        }
    }

    private fun addGeneratedSources() {
        if (kotlinOutputDir.exists()) {
            log.info("Adding generated Kotlin sources: $kotlinOutputDir")
            project!!.addCompileSourceRoot(kotlinOutputDir.absolutePath)
        }

        if (javaOutputDir.exists() && javaOutputDir != kotlinOutputDir) {
            log.info("Adding generated Java sources: $javaOutputDir")
            project!!.addCompileSourceRoot(javaOutputDir.absolutePath)
        }

        if (resourceOutputDir.exists()) {
            log.info("Adding generated resources: $resourceOutputDir")
            val resource = Resource()
            resource.directory = resourceOutputDir.absolutePath
            project!!.addResource(resource)
        }
    }

    companion object {
        private const val KSP_PLUGIN_ID = "com.google.devtools.ksp.symbol-processing"
    }
}
