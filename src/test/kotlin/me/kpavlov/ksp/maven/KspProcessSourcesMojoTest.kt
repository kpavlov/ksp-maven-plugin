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
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createKspApiJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createKspPluginJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createMockKspProcessorJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createRegularJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createSourceFile
import org.apache.maven.plugin.MojoFailureException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.io.File

@ExtendWith(MockitoExtension::class)
class KspProcessSourcesMojoTest : AbstractKspProcessMojoTest<KspProcessSourcesMojo>() {
    override fun createMojo(): KspProcessSourcesMojo =
        object : KspProcessSourcesMojo() {
            override val kspFactory: KspFactory =
                KspFactory { kspConfig, symbolProcessorProviders, _ ->
                    capturedKSPConfig = kspConfig
                    capturedSymbolProcessorProviders = symbolProcessorProviders
                    processing
                }
        }

    @Test
    fun `should skip execution when skip property is true`() {
        configureMojo(mojo = mojo, project = project, skip = true)

        mojo.execute()
    }

    @Test
    fun `should create output directories`() {
        val kspProcessors = createMockKspProcessorJar(tempDir)
        project.artifacts =
            setOf(kspProcessors, createKspPluginJar(tempDir), createKspApiJar(tempDir))
        createSourceFile(project)

        try {
            mojo.execute()
        } catch (_: Exception) {
        }

        buildDir.resolve("generated-sources/ksp").shouldExist()
        buildDir.resolve("ksp-classes").shouldExist()
        buildDir.resolve("generated-resources/ksp").shouldExist()
        buildDir.resolve("ksp").shouldExist()
        buildDir.resolve("ksp-cache").shouldExist()
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
    fun `should run KSP processing`() {
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)

        mojo.execute()
    }

    @Test
    fun `should handle KSP processing error exit code`() {
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR)

        val exception = shouldThrow<MojoFailureException> { mojo.execute() }
        exception.message shouldContain "KotlinSymbolProcessing failed with exit code"
    }

    @Test
    fun `should continue when ignoreProcessingErrors is true and processing fails`() {
        configureMojo(mojo = mojo, project = project, ignoreProcessingErrors = true)
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR)

        mojo.execute()
    }

    @Test
    fun `should add all generated sources and resources to project when configured`() {
        val javaOutputDir = tempDir.resolve("java-output").toFile()
        configureMojo(
            mojo = mojo,
            project = project,
            javaOutputDir = javaOutputDir,
            addGeneratedSourcesToCompile = true,
        )

        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createSourceFile(project)

        val kotlinOutputDir = buildDir.resolve("generated-sources/ksp")
        kotlinOutputDir.mkdirs()
        kotlinOutputDir.resolve("Generated.kt").writeText("class Generated")

        javaOutputDir.mkdirs()
        javaOutputDir.resolve("Generated.java").writeText("public class Generated {}")

        val resourceOutputDir = buildDir.resolve("generated-resources/ksp")
        resourceOutputDir.mkdirs()
        resourceOutputDir.resolve("generated.txt").writeText("Generated resource")

        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)

        mojo.execute()

        project.compileSourceRoots shouldContainAll
            listOf(
                kotlinOutputDir.absolutePath,
                javaOutputDir.absolutePath,
            )
        project.resources.any { it.directory == resourceOutputDir.absolutePath } shouldBe true
    }

    @Test
    fun `should not add generated sources when addGeneratedSourcesToCompile is false`() {
        configureMojo(mojo = mojo, project = project, addGeneratedSourcesToCompile = false)
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createSourceFile(project)

        val kotlinOutputDir = buildDir.resolve("generated-sources/ksp")
        kotlinOutputDir.mkdirs()
        kotlinOutputDir.resolve("Generated.kt").writeText("class Generated")
        val initialCompileRootsSize = project.compileSourceRoots.size
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)

        mojo.execute()

        project.compileSourceRoots shouldHaveSize initialCompileRootsSize
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
        createSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)

        mojo.execute()
    }

    // ── Processor filtering ──────────────────────────────────────────────────
    // HelloProcessorProvider is registered in the test classpath via
    // META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider
    // FQCN: me.kpavlov.ksp.maven.testprocessor.HelloProcessorProvider

    @Nested
    inner class ProcessorFilteringTest {
        private val helloFqcn = "me.kpavlov.ksp.maven.testprocessor.HelloProcessorProvider"

        private fun setupAndExecute() {
            val kspProcessor = createMockKspProcessorJar(tempDir)
            project.artifacts = setOf(kspProcessor)
            createSourceFile(project)
            whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.OK)
            mojo.execute()
        }

        @Test
        fun `processorIncludes with matching pattern passes provider to factory`() {
            configureMojo(
                mojo = mojo,
                project = project,
                processorIncludes = listOf("me.kpavlov.ksp.maven.testprocessor.*"),
            )

            setupAndExecute()

            val names = capturedSymbolProcessorProviders.map { it::class.qualifiedName }
            names shouldContainAll listOf(helloFqcn)
        }

        @Test
        fun `processorIncludes with non-matching pattern removes all providers`() {
            configureMojo(
                mojo = mojo,
                project = project,
                processorIncludes = listOf("org.unrelated.*"),
            )

            setupAndExecute()

            capturedSymbolProcessorProviders.shouldBeEmpty()
        }

        @Test
        fun `processorExcludes with matching pattern removes provider from factory call`() {
            configureMojo(
                mojo = mojo,
                project = project,
                processorExcludes = listOf(helloFqcn),
            )

            setupAndExecute()

            val names = capturedSymbolProcessorProviders.map { it::class.qualifiedName }
            names.shouldBeEmpty()
        }

        @Test
        fun `processorExcludes with non-matching pattern keeps all providers`() {
            configureMojo(
                mojo = mojo,
                project = project,
                processorExcludes = listOf("com.unrelated.*"),
            )

            setupAndExecute()

            val names = capturedSymbolProcessorProviders.map { it::class.qualifiedName }
            names shouldContainAll listOf(helloFqcn)
        }

        @Test
        fun `no filters passes all discovered providers to factory`() {
            setupAndExecute()

            val names = capturedSymbolProcessorProviders.map { it::class.qualifiedName }
            names shouldContainAll listOf(helloFqcn)
        }
    }
}
