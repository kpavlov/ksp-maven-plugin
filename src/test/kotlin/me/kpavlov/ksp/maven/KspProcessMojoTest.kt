package me.kpavlov.ksp.maven

import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.project.MavenProject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Unit tests for KspProcessMojo
 */
class KspProcessMojoTest {

    @TempDir
    lateinit var tempDir: Path

    private var mojo: KspProcessMojo? = null
    private var project: MavenProject? = null

    @BeforeEach
    fun setup() {
        mojo = KspProcessMojo()

        val baseDir = tempDir.toFile()

        // Create a proper MavenProject with basedir set
        project = MavenProject()
        project!!.file = baseDir.resolve("pom.xml")

        // Create pom.xml to establish basedir
        project!!.file.parentFile.mkdirs()
        project!!.file.writeText("""
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>test</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0</version>
            </project>
        """.trimIndent())

        // Set up build directory
        val buildDir = baseDir.resolve("target")
        buildDir.mkdirs()
        project!!.build.directory = buildDir.absolutePath
        project!!.build.sourceDirectory = baseDir.resolve("src/main/kotlin").absolutePath
        project!!.build.outputDirectory = buildDir.resolve("classes").absolutePath

        // Set artifact ID for module name
        project!!.artifactId = "test-project"
        project!!.model.artifactId = "test-project"

        // Inject project into mojo using reflection
        val projectField = KspProcessMojo::class.java.getDeclaredField("project")
        projectField.isAccessible = true
        projectField.set(mojo, project)

        // Set default values for required parameters
        setPrivateField("sourceDirectory", baseDir.resolve("src/main/kotlin"))
        setPrivateField("kotlinOutputDir", buildDir.resolve("generated-sources/ksp"))
        setPrivateField("javaOutputDir", buildDir.resolve("generated-sources/ksp"))
        setPrivateField("classOutputDir", buildDir.resolve("ksp-classes"))
        setPrivateField("resourceOutputDir", buildDir.resolve("generated-resources/ksp"))
        setPrivateField("kspOutputDir", buildDir.resolve("ksp"))
        setPrivateField("cachesDir", buildDir.resolve("ksp-cache"))
        setPrivateField("moduleName", "test-project")
        setPrivateField("jvmTarget", "11")
        setPrivateField("kspVersion", "2.3.2")
        setPrivateField("kotlinVersion", "2.2.21")
        setPrivateField("skip", false)
        setPrivateField("addGeneratedSourcesToCompile", true)
    }

    private fun setPrivateField(fieldName: String, value: Any) {
        val field = KspProcessMojo::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(mojo!!, value)
    }

    private fun getPrivateField(fieldName: String): Any? {
        val field = KspProcessMojo::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(mojo!!)
    }

    @Test
    fun `should skip execution when skip property is true`() {
        // Given
        setPrivateField("skip", true)

        // When/Then - should not throw any exception
        mojo!!.execute()
    }

    @Test
    fun `should create output directories`() {
        // Given
        val kspProcessors = createMockKspProcessorJar()
        project!!.artifacts = setOf(kspProcessors, createKspPluginJar(), createKspApiJar())

        // Create source files
        val srcDir = File(project!!.build.sourceDirectory)
        srcDir.mkdirs()
        srcDir.resolve("Test.kt").writeText("class Test")

        // When
        try {
            mojo!!.execute()
        } catch (e: Exception) {
            // Expected to fail during compilation, but directories should be created
        }

        // Then
        assertThat(getPrivateField("kotlinOutputDir") as File).exists()
        assertThat(getPrivateField("javaOutputDir") as File).exists()
        assertThat(getPrivateField("classOutputDir") as File).exists()
        assertThat(getPrivateField("resourceOutputDir") as File).exists()
        assertThat(getPrivateField("kspOutputDir") as File).exists()
        assertThat(getPrivateField("cachesDir") as File).exists()
    }

    @Test
    fun `should detect KSP processor with correct META-INF entry`() {
        // Given
        val kspProcessor = createMockKspProcessorJar()
        project!!.artifacts = setOf(kspProcessor)

        // When
        val method = KspProcessMojo::class.java.getDeclaredMethod("findKspProcessors")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val processors = method.invoke(mojo!!) as List<File>

        // Then
        assertThat(processors).hasSize(1)
        assertThat(processors[0]).isEqualTo(kspProcessor.file)
    }

    @Test
    fun `should not detect regular JAR without KSP META-INF entry`() {
        // Given
        val regularJar = createRegularJar()
        project!!.artifacts = setOf(regularJar)

        // When
        val method = KspProcessMojo::class.java.getDeclaredMethod("findKspProcessors")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val processors = method.invoke(mojo!!) as List<File>

        // Then
        assertThat(processors).isEmpty()
    }

    @Test
    fun `should skip execution when no KSP processors found`() {
        // Given - no processors in dependencies
        project!!.artifacts = emptySet()

        // When/Then - should not throw exception
        mojo!!.execute()
    }

    @Test
    fun `should throw exception when KSP plugin JAR not found`() {
        // Given
        val kspProcessor = createMockKspProcessorJar()
        project!!.artifacts = setOf(kspProcessor) // Missing KSP plugin JARs

        // Create source files
        val srcDir = File(project!!.build.sourceDirectory)
        srcDir.mkdirs()
        srcDir.resolve("Test.kt").writeText("class Test")

        // When/Then
        assertThatThrownBy { mojo!!.execute() }
            .isInstanceOf(MojoExecutionException::class.java)
            .hasMessageContaining("KSP plugin JAR not found")
    }

    @Test
    fun `should collect Kotlin source files from source directory`() {
        // Given
        val srcDir = File(project!!.build.sourceDirectory)
        srcDir.mkdirs()
        srcDir.resolve("Test1.kt").writeText("class Test1")

        val subDir = srcDir.resolve("sub")
        subDir.mkdirs()
        subDir.resolve("Test2.kt").writeText("class Test2")

        // Also create non-Kotlin file
        srcDir.resolve("README.md").writeText("# Test")

        setPrivateField("sourceDirectory", srcDir)

        // When
        val method = KspProcessMojo::class.java.getDeclaredMethod("collectSourceFiles")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sources = method.invoke(mojo!!) as List<File>

        // Then
        assertThat(sources).hasSize(2)
        assertThat(sources.map { it.name }).containsExactlyInAnyOrder("Test1.kt", "Test2.kt")
    }

    @Test
    fun `should build compiler arguments with all required options`() {
        // Given
        val kspProcessor = createMockKspProcessorJar()
        val kspPlugin = createKspPluginJar()
        val kspApi = createKspApiJar()
        project!!.artifacts = setOf(kspProcessor, kspPlugin, kspApi)

        setPrivateField("incremental", true)
        setPrivateField("apOptions", mutableMapOf("option1" to "value1", "option2" to "value2"))

        val processors = listOf(kspProcessor.file)

        // When
        val method = KspProcessMojo::class.java.getDeclaredMethod(
            "buildCompilerArgs",
            MutableList::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val args = method.invoke(mojo!!, processors) as List<String>

        // Then
        assertThat(args).contains("-Xplugin=${kspPlugin.file.absolutePath}")
        assertThat(args).contains("-Xplugin=${kspApi.file.absolutePath}")
        assertThat(args).contains("-Xallow-no-source-files")
        assertThat(args).contains("-module-name")
        assertThat(args).contains("test-project")
        assertThat(args).contains("-jvm-target")
        assertThat(args).contains("11")

        // Check KSP plugin options
        assertThat(args.joinToString(" "))
            .contains("plugin:com.google.devtools.ksp.symbol-processing:apclasspath=")
        assertThat(args.joinToString(" "))
            .contains("plugin:com.google.devtools.ksp.symbol-processing:incremental=true")
        assertThat(args.joinToString(" "))
            .contains("plugin:com.google.devtools.ksp.symbol-processing:apoption=option1=value1")
        assertThat(args.joinToString(" "))
            .contains("plugin:com.google.devtools.ksp.symbol-processing:apoption=option2=value2")
    }

    // Helper methods to create mock JARs

    private fun createMockKspProcessorJar(): Artifact {
        val jarFile = tempDir.resolve("test-processor.jar").toFile()
        JarOutputStream(jarFile.outputStream()).use { jos ->
            // Add KSP processor service entry
            val entry = JarEntry("META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider")
            jos.putNextEntry(entry)
            jos.write("com.example.TestProcessor".toByteArray())
            jos.closeEntry()
        }

        return createArtifact("com.example", "test-processor", "1.0.0", jarFile)
    }

    private fun createKspPluginJar(): Artifact {
        val jarFile = tempDir.resolve("symbol-processing-aa-embeddable.jar").toFile()
        JarOutputStream(jarFile.outputStream()).use { jos ->
            val entry = JarEntry("META-INF/MANIFEST.MF")
            jos.putNextEntry(entry)
            jos.write("Manifest-Version: 1.0\n".toByteArray())
            jos.closeEntry()
        }

        return createArtifact(
            "com.google.devtools.ksp",
            "symbol-processing-aa-embeddable",
            "2.3.2",
            jarFile
        )
    }

    private fun createKspApiJar(): Artifact {
        val jarFile = tempDir.resolve("symbol-processing-api.jar").toFile()
        JarOutputStream(jarFile.outputStream()).use { jos ->
            val entry = JarEntry("META-INF/MANIFEST.MF")
            jos.putNextEntry(entry)
            jos.write("Manifest-Version: 1.0\n".toByteArray())
            jos.closeEntry()
        }

        return createArtifact(
            "com.google.devtools.ksp",
            "symbol-processing-api",
            "2.3.2",
            jarFile
        )
    }

    private fun createRegularJar(): Artifact {
        val jarFile = tempDir.resolve("regular-library.jar").toFile()
        JarOutputStream(jarFile.outputStream()).use { jos ->
            val entry = JarEntry("META-INF/MANIFEST.MF")
            jos.putNextEntry(entry)
            jos.write("Manifest-Version: 1.0\n".toByteArray())
            jos.closeEntry()
        }

        return createArtifact("com.example", "regular-lib", "1.0.0", jarFile)
    }

    private fun createArtifact(
        groupId: String,
        artifactId: String,
        version: String,
        file: File
    ): Artifact {
        val artifact = DefaultArtifact(
            groupId,
            artifactId,
            version,
            "compile",
            "jar",
            null,
            DefaultArtifactHandler("jar")
        )
        artifact.file = file
        return artifact
    }
}
