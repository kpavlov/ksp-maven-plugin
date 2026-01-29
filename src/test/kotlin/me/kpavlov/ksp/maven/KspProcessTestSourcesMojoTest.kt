package me.kpavlov.ksp.maven

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.kpavlov.ksp.maven.KspMojoTestHelpers.configureMojo
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createJarWithEntries
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createMockKspProcessorJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createRegularJar
import org.apache.maven.plugin.MojoFailureException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.io.File

@ExtendWith(MockitoExtension::class)
class KspProcessTestSourcesMojoTest : AbstractKspProcessMojoTest<KspProcessTestSourcesMojo>() {
    override fun createMojo(): KspProcessTestSourcesMojo =
        object : KspProcessTestSourcesMojo() {
            override val kspFactory: KspFactory =
                KspFactory { kspConfig, symbolProcessorProviders, _ ->
                    capturedKSPConfig = kspConfig
                    capturedSymbolProcessorProviders = symbolProcessorProviders
                    processing
                }
        }

    @BeforeEach
    fun setupTestSourceDirectory() {
        // Set up test source directory
        val testSrcDir = baseDir.resolve("src/test/kotlin")
        project.build.testSourceDirectory = testSrcDir.absolutePath
        // Also set sourceDirectory so createSourceFile() in abstract tests works correctly
        project.build.sourceDirectory = testSrcDir.absolutePath

        // Configure mojo with test source directory
        configureMojo(
            mojo = mojo,
            project = project,
            sourceDirectory = testSrcDir,
            moduleName = "test-project",
            jvmTarget = "11",
            languageVersion = "2.2",
            apiVersion = "2.2",
            skip = false,
            addGeneratedSourcesToCompile = true,
        )
    }

    @Test
    fun `should skip execution when skipTest property is true`() {
        configureMojo(mojo = mojo, project = project, skip = true)

        mojo.execute()
    }

    @Test
    fun `should create test output directories`() {
        val kspProcessors = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessors)
        createTestSourceFile(project)

        // Test verifies directory creation side effect; execution may fail due to incomplete setup
        try {
            mojo.execute()
        } catch (_: Exception) {
        }

        // Verify test-specific output directories
        buildDir.resolve("generated-test-sources/ksp").shouldExist()
        buildDir.resolve("ksp-test-classes").shouldExist()
        buildDir.resolve("generated-test-resources/ksp").shouldExist()
        buildDir.resolve("ksp-test").shouldExist()
        buildDir.resolve("ksp-test-cache").shouldExist()
    }

    @Test
    fun `should detect KSP processor with correct META-INF entry`() {
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)

        val method = AbstractKspProcessMojo::class.java.getDeclaredMethod("findKspProcessors")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val processors = method.invoke(mojo) as List<File>

        processors shouldHaveSize 1
        processors[0] shouldBe kspProcessor.file
    }

    @Test
    fun `should not detect regular JAR without KSP META-INF entry`() {
        val regularJar = createRegularJar(tempDir)
        project.artifacts = setOf(regularJar)

        val method = AbstractKspProcessMojo::class.java.getDeclaredMethod("findKspProcessors")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val processors = method.invoke(mojo) as List<File>

        processors.shouldBeEmpty()
    }

    @Test
    fun `should skip execution when no KSP processors found`() {
        project.artifacts = emptySet()

        mojo.execute()
    }

    @Test
    fun `should run KSP processing for test sources`() {
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createTestSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)

        mojo.execute()
    }

    @Test
    fun `should handle KSP processing error exit code`() {
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createTestSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR)

        val exception = shouldThrow<MojoFailureException> { mojo.execute() }
        exception.message shouldContain "KotlinSymbolProcessing failed with exit code"
    }

    @Test
    fun `should continue when ignoreProcessingErrors is true and processing fails`() {
        configureMojo(mojo = mojo, project = project, ignoreProcessingErrors = true)
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createTestSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR)

        mojo.execute()
    }

    @Test
    fun `should add all generated test sources and resources to project when configured`() {
        val javaOutputDir = tempDir.resolve("java-test-output").toFile()
        configureMojo(
            mojo = mojo,
            project = project,
            javaOutputDir = javaOutputDir,
            addGeneratedSourcesToCompile = true,
        )

        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createTestSourceFile(project)

        val kotlinOutputDir = buildDir.resolve("generated-test-sources/ksp")
        kotlinOutputDir.mkdirs()
        kotlinOutputDir.resolve("GeneratedTest.kt").writeText("class GeneratedTest")

        javaOutputDir.mkdirs()
        javaOutputDir.resolve("GeneratedTest.java").writeText("public class GeneratedTest {}")

        val resourceOutputDir = buildDir.resolve("generated-test-resources/ksp")
        resourceOutputDir.mkdirs()
        resourceOutputDir.resolve("generated-test.txt").writeText("Generated test resource")

        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)

        mojo.execute()

        // Verify test sources are added to test compile roots
        project.testCompileSourceRoots shouldContainAll
            listOf(
                kotlinOutputDir.absolutePath,
                javaOutputDir.absolutePath,
            )

        // Verify test resources are added
        project.testResources.any { it.directory == resourceOutputDir.absolutePath } shouldBe true
    }

    @Test
    fun `should not add generated test sources when addGeneratedSourcesToCompile is false`() {
        configureMojo(mojo = mojo, project = project, addGeneratedSourcesToCompile = false)
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createTestSourceFile(project)

        val kotlinOutputDir = buildDir.resolve("generated-test-sources/ksp")
        kotlinOutputDir.mkdirs()
        kotlinOutputDir.resolve("GeneratedTest.kt").writeText("class GeneratedTest")
        val initialTestCompileRootsSize = project.testCompileSourceRoots.size
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)

        mojo.execute()

        project.testCompileSourceRoots shouldHaveSize initialTestCompileRootsSize
    }

    @Test
    fun `should handle debug mode with multiple processors`() {
        configureMojo(mojo = mojo, project = project, debug = true)

        val processor1 = createMockKspProcessorJar(tempDir)
        val processor2 = tempDir.resolve("test-processor2.jar").toFile()
        createJarWithEntries(
            processor2,
            mapOf(
                "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider" to
                    "com.example.TestProcessor2".toByteArray(),
            ),
        )
        val artifact2 =
            KspMojoTestHelpers.createArtifact(
                "com.example",
                "test-processor2",
                "1.0.0",
                processor2,
            )

        project.artifacts = setOf(processor1, artifact2)
        createTestSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)

        mojo.execute()
    }

    @Test
    fun `should verify test scope is used`() {
        // Verify the mojo is using TEST scope by checking output directories
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createTestSourceFile(project)

        // Test verifies directory creation side effect; execution may fail due to incomplete setup
        try {
            mojo.execute()
        } catch (_: Exception) {
        }

        // Test scope should create test-specific directories
        buildDir.resolve("generated-test-sources/ksp").shouldExist()
        buildDir.resolve("ksp-test").shouldExist()
    }

    private fun createTestSourceFile(
        project: org.apache.maven.project.MavenProject,
        fileName: String = "TestClass.kt",
        content: String = "class TestClass",
    ): File {
        val testSrcDir = File(project.build.testSourceDirectory)
        testSrcDir.mkdirs()
        return testSrcDir.resolve(fileName).apply { writeText(content) }
    }
}
