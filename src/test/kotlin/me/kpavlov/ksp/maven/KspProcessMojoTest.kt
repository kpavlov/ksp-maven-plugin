package me.kpavlov.ksp.maven

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPConfig
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import me.kpavlov.ksp.maven.KspMojoTestHelpers.configureMojo
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createJarWithEntries
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createKspApiJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createKspPluginJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createMockKspProcessorJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createRegularJar
import me.kpavlov.ksp.maven.KspMojoTestHelpers.createSourceFile
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.project.MavenProject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
class KspProcessMojoTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var mojo: KspProcessMojo
    private lateinit var project: MavenProject
    private lateinit var baseDir: File
    private lateinit var buildDir: File

    @Mock
    private lateinit var processing: KotlinSymbolProcessing

    private lateinit var capturedKSPConfig: KSPConfig
    private lateinit var capturedSymbolProcessorProviders: List<SymbolProcessorProvider>

    @BeforeEach
    fun beforeEach() {
        mojo =
            KspProcessMojo(
                kspFactory =
                    object : KspFactory {
                        override fun create(
                            kspConfig: KSPConfig,
                            symbolProcessorProviders: List<SymbolProcessorProvider>,
                            logger: KspLogger,
                        ): KotlinSymbolProcessing {
                            capturedKSPConfig = kspConfig
                            capturedSymbolProcessorProviders = symbolProcessorProviders
                            return processing
                        }
                    },
            )

        baseDir = tempDir.toFile()
        buildDir = baseDir.resolve("target")

        project = MavenProject()
        project.file = baseDir.resolve("pom.xml")
        project.file.parentFile.mkdirs()
        project.file.writeText(
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>test</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0</version>
            </project>
            """.trimIndent(),
        )

        buildDir.mkdirs()
        project.build.directory = buildDir.absolutePath
        project.build.sourceDirectory = baseDir.resolve("src/main/kotlin").absolutePath
        project.build.outputDirectory = buildDir.resolve("classes").absolutePath
        project.artifactId = "test-project"
        project.model.artifactId = "test-project"

        configureMojo(
            mojo = mojo,
            project = project,
            sourceDirectory = baseDir.resolve("src/main/kotlin"),
            kotlinOutputDir = buildDir.resolve("generated-sources/ksp"),
            javaOutputDir = buildDir.resolve("generated-sources/ksp"),
            classOutputDir = buildDir.resolve("ksp-classes"),
            resourceOutputDir = buildDir.resolve("generated-resources/ksp"),
            kspOutputDir = buildDir.resolve("ksp"),
            cachesDir = buildDir.resolve("ksp-cache"),
            moduleName = "test-project",
            jvmTarget = "11",
            languageVersion = "2.2",
            apiVersion = "2.2",
            skip = false,
            addGeneratedSourcesToCompile = true,
        )
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

        assertThat(buildDir.resolve("generated-sources/ksp")).exists()
        assertThat(buildDir.resolve("ksp-classes")).exists()
        assertThat(buildDir.resolve("generated-resources/ksp")).exists()
        assertThat(buildDir.resolve("ksp")).exists()
        assertThat(buildDir.resolve("ksp-cache")).exists()
    }

    @Test
    fun `should detect KSP processor with correct META-INF entry`() {
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)

        val method = KspProcessMojo::class.java.getDeclaredMethod("findKspProcessors")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val processors = method.invoke(mojo) as List<File>

        assertThat(processors).hasSize(1)
        assertThat(processors[0]).isEqualTo(kspProcessor.file)
    }

    @Test
    fun `should not detect regular JAR without KSP META-INF entry`() {
        val regularJar = createRegularJar(tempDir)
        project.artifacts = setOf(regularJar)

        val method = KspProcessMojo::class.java.getDeclaredMethod("findKspProcessors")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val processors = method.invoke(mojo) as List<File>

        assertThat(processors).isEmpty()
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
    fun `should throw exception when KSP plugin JAR not found`() {
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createSourceFile(project)
        whenever(processing.execute()).thenThrow(RuntimeException("Expected exception"))

        assertThatThrownBy { mojo.execute() }
            .isInstanceOf(MojoExecutionException::class.java)
            .hasMessageContaining("Failed to execute KotlinSymbolProcessing")
    }

    @Test
    fun `should handle KSP processing error exit code`() {
        val kspProcessor = createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        createSourceFile(project)
        whenever(processing.execute()).thenReturn(KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR)

        assertThatThrownBy { mojo.execute() }
            .isInstanceOf(MojoFailureException::class.java)
            .hasMessageContaining("KotlinSymbolProcessing failed with exit code")
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

        assertThat(project.compileSourceRoots)
            .contains(kotlinOutputDir.absolutePath)
            .contains(javaOutputDir.absolutePath)
        assertThat(project.resources)
            .anyMatch { it.directory == resourceOutputDir.absolutePath }
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

        assertThat(project.compileSourceRoots).hasSize(initialCompileRootsSize)
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
}
