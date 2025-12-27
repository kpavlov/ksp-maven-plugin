package me.kpavlov.ksp.maven

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPConfig
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.project.MavenProject
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
abstract class AbstractKspProcessMojoTest<T : AbstractKspProcessMojo> {
    protected lateinit var mojo: T

    protected lateinit var baseDir: File
    protected lateinit var buildDir: File

    protected lateinit var project: MavenProject

    @Mock
    protected lateinit var processing: KotlinSymbolProcessing

    protected lateinit var capturedKSPConfig: KSPConfig
    protected lateinit var capturedSymbolProcessorProviders: List<SymbolProcessorProvider>

    @TempDir
    lateinit var tempDir: Path

    protected abstract fun createMojo(): T

    @BeforeEach
    fun beforeEach() {
        mojo = createMojo()

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

        KspMojoTestHelpers.configureMojo(
            mojo = mojo,
            project = project,
            sourceDirectory = baseDir.resolve("src/main/kotlin"),
            moduleName = "test-project",
            jvmTarget = "11",
            languageVersion = "2.2",
            apiVersion = "2.2",
            skip = false,
            addGeneratedSourcesToCompile = true,
        )
    }

    @Test
    fun `should throw exception when KSP plugin JAR not found`() {
        val kspProcessor = KspMojoTestHelpers.createMockKspProcessorJar(tempDir)
        project.artifacts = setOf(kspProcessor)
        KspMojoTestHelpers.createSourceFile(project)
        whenever(processing.execute()).thenThrow(RuntimeException("Expected exception"))

        val exception = shouldThrow<MojoExecutionException> { mojo.execute() }
        exception.message shouldContain "Failed to execute KotlinSymbolProcessing"
    }
}
