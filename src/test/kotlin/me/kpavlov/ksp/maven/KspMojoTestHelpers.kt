package me.kpavlov.ksp.maven

import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Test helpers for KspProcessMojo testing.
 * Provides utilities for configuring mojos and creating test artifacts.
 */
object KspMojoTestHelpers {
    /**
     * Configures the mojo with the given parameters using field injection.
     * This mimics how Maven would configure the plugin, but uses reflection
     * to set fields directly since we're in a test environment.
     */
    fun configureMojo(
        mojo: KspProcessMojo,
        project: MavenProject,
        sourceDirectory: File? = null,
        sourceDirs: List<File>? = null,
        kotlinOutputDir: File? = null,
        javaOutputDir: File? = null,
        classOutputDir: File? = null,
        resourceOutputDir: File? = null,
        kspOutputDir: File? = null,
        cachesDir: File? = null,
        incremental: Boolean? = null,
        incrementalLog: Boolean? = null,
        jvmDefaultMode: String? = null,
        languageVersion: String? = null,
        apiVersion: String? = null,
        ignoreProcessingErrors: Boolean? = null,
        allWarningsAsErrors: Boolean? = null,
        mapAnnotationArgumentsInJava: Boolean? = null,
        debug: Boolean? = null,
        apOptions: Map<String, String>? = null,
        moduleName: String? = null,
        jvmTarget: String? = null,
        skip: Boolean? = null,
        addGeneratedSourcesToCompile: Boolean? = null,
    ) {
        // Set the project
        setField(mojo, "project", project)

        // Set all other parameters
        sourceDirectory?.let { setField(mojo, "sourceDirectory", it) }
        sourceDirs?.let { setField(mojo, "sourceDirs", it.toMutableList()) }
        kotlinOutputDir?.let { setField(mojo, "kotlinOutputDir", it) }
        javaOutputDir?.let { setField(mojo, "javaOutputDir", it) }
        classOutputDir?.let { setField(mojo, "classOutputDir", it) }
        resourceOutputDir?.let { setField(mojo, "resourceOutputDir", it) }
        kspOutputDir?.let { setField(mojo, "kspOutputDir", it) }
        cachesDir?.let { setField(mojo, "cachesDir", it) }
        incremental?.let { setField(mojo, "incremental", it) }
        incrementalLog?.let { setField(mojo, "incrementalLog", it) }
        jvmDefaultMode?.let { setField(mojo, "jvmDefaultMode", it) }
        languageVersion?.let { setField(mojo, "languageVersion", it) }
        apiVersion?.let { setField(mojo, "apiVersion", it) }
        ignoreProcessingErrors?.let { setField(mojo, "ignoreProcessingErrors", it) }
        allWarningsAsErrors?.let { setField(mojo, "allWarningsAsErrors", it) }
        mapAnnotationArgumentsInJava?.let { setField(mojo, "mapAnnotationArgumentsInJava", it) }
        debug?.let { setField(mojo, "debug", it) }
        apOptions?.let { setField(mojo, "apOptions", it.toMutableMap()) }
        moduleName?.let { setField(mojo, "moduleName", it) }
        jvmTarget?.let { setField(mojo, "jvmTarget", it) }
        skip?.let { setField(mojo, "skip", it) }
        addGeneratedSourcesToCompile?.let { setField(mojo, "addGeneratedSourcesToCompile", it) }
    }

    /**
     * Sets a private field on the mojo using reflection.
     */
    private fun setField(
        mojo: KspProcessMojo,
        fieldName: String,
        value: Any,
    ) {
        val field = KspProcessMojo::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(mojo, value)
    }

    /**
     * Creates a source file in the project's source directory.
     */
    fun createSourceFile(
        project: MavenProject,
        fileName: String = "Test.kt",
        content: String = "class Test",
    ): File {
        val srcDir = File(project.build.sourceDirectory)
        srcDir.mkdirs()
        return srcDir.resolve(fileName).apply { writeText(content) }
    }

    /**
     * Creates a mock KSP processor JAR with the proper META-INF service entry.
     */
    fun createMockKspProcessorJar(tempDir: Path): Artifact {
        val jarFile = tempDir.resolve("test-processor.jar").toFile()
        createJarWithEntries(
            jarFile,
            mapOf(
                "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider" to
                    "com.example.TestProcessor".toByteArray(),
            ),
        )
        return createArtifact("com.example", "test-processor", "1.0.0", jarFile)
    }

    /**
     * Creates a KSP plugin JAR (symbol-processing-aa-embeddable).
     */
    fun createKspPluginJar(tempDir: Path): Artifact {
        val jarFile = tempDir.resolve("symbol-processing-aa-embeddable.jar").toFile()
        createJarWithManifest(jarFile)
        return createArtifact(
            "com.google.devtools.ksp",
            "symbol-processing-aa-embeddable",
            "2.3.2",
            jarFile,
        )
    }

    /**
     * Creates a KSP API JAR (symbol-processing-api).
     */
    fun createKspApiJar(tempDir: Path): Artifact {
        val jarFile = tempDir.resolve("symbol-processing-api.jar").toFile()
        createJarWithManifest(jarFile)
        return createArtifact(
            "com.google.devtools.ksp",
            "symbol-processing-api",
            "2.3.2",
            jarFile,
        )
    }

    /**
     * Creates a regular JAR without KSP processor entries.
     */
    fun createRegularJar(tempDir: Path): Artifact {
        val jarFile = tempDir.resolve("regular-library.jar").toFile()
        createJarWithManifest(jarFile)
        return createArtifact("com.example", "regular-lib", "1.0.0", jarFile)
    }

    /**
     * Creates a JAR file with only a manifest.
     */
    fun createJarWithManifest(jarFile: File) {
        createJarWithEntries(
            jarFile,
            mapOf("META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n".toByteArray()),
        )
    }

    /**
     * Creates a JAR file with the specified entries.
     */
    fun createJarWithEntries(
        jarFile: File,
        entries: Map<String, ByteArray>,
    ) {
        JarOutputStream(jarFile.outputStream()).use { jos ->
            entries.forEach { (path, content) ->
                val entry = JarEntry(path)
                jos.putNextEntry(entry)
                jos.write(content)
                jos.closeEntry()
            }
        }
    }

    /**
     * Creates a Maven artifact with the specified coordinates and file.
     */
    fun createArtifact(
        groupId: String,
        artifactId: String,
        version: String,
        file: File,
    ): Artifact {
        val artifact =
            DefaultArtifact(
                groupId,
                artifactId,
                version,
                "compile",
                "jar",
                null,
                DefaultArtifactHandler("jar"),
            )
        artifact.file = file
        return artifact
    }
}
