package me.kpavlov.ksp.maven

import org.apache.maven.shared.verifier.Verifier
import org.apache.maven.shared.verifier.util.ResourceExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Integration tests for KSP Maven Plugin using Maven Verifier
 */
class KspProcessMojoIT {

    companion object {
        private lateinit var pluginVersion: String
        private lateinit var kspVersion: String
        private lateinit var kotlinVersion: String
        private lateinit var localRepoPath: String
        private val projectDir: String = TestEnvironment.projectDir

        @JvmStatic
        @BeforeAll
        fun setupVersions() {
            // Read versions from system properties or use defaults
            pluginVersion = System.getProperty("project.version", "0.1.0-SNAPSHOT")
            kspVersion = System.getProperty("ksp.version", "2.3.2")
            kotlinVersion = System.getProperty("kotlin.version", "2.2.21")

            // Use local repository in target directory
            localRepoPath = "$projectDir/target/local-repo"
        }
    }

    private val testProjectsDir: Path = TestEnvironment.testProjectsDir

    @Test
    fun `should generate code using test KSP processor`() {
        // Given
        val testProject = createTestProject("simple-project")

        // Debug: Print local repo path and POM
        println("=== Using local repo: $localRepoPath ===")
        println("=== Test project POM ===")
        println(testProject.resolve("pom.xml").readText())
        println("========================")

        // When
        val verifier = Verifier(testProject.absolutePath)
        verifier.isAutoclean = false
        verifier.setLocalRepo(localRepoPath)
        verifier.addCliArgument("clean")
        verifier.addCliArgument("compile")
        verifier.execute()

        // Then
        verifier.verifyErrorFreeLog()

        // Debug: List what WAS generated
        val kspDir = testProject.resolve("target/generated-sources/ksp")
        println("=== KSP output directory exists: ${kspDir.exists()} ===")
        if (kspDir.exists()) {
            kspDir.walkTopDown().forEach { file ->
                println("Generated: ${file.absolutePath}")
            }
        }
        val targetDir = testProject.resolve("target")
        if (targetDir.exists()) {
            println("=== Target directory contents ===")
            targetDir.listFiles()?.forEach { println(it.name) }
        }

        // Verify generated file exists
        val generatedFile = testProject.resolve(
            "target/generated-sources/ksp/com/example/TestClassGreeting.kt"
        )
        assertThat(generatedFile)
            .exists()
            .isFile()

        // Verify generated content
        val content = generatedFile.readText()
        assertThat(content).contains("class TestClassGreeting")
        assertThat(content).contains("fun greet(): String = \"Hello, Integration Test!\"")
        assertThat(content).contains("const val GENERATED_FOR = \"TestClass\"")
    }

    @Test
    fun `should handle project with no KSP processors`() {
        // Given
        val testProject = createTestProject("no-processor-project", includeProcessor = false)

        // When
        val verifier = Verifier(testProject.absolutePath)
        verifier.isAutoclean = false
        verifier.setLocalRepo(localRepoPath)
        verifier.addCliArgument("clean")
        verifier.addCliArgument("compile")
        verifier.execute()

        // Then
        verifier.verifyErrorFreeLog()
        verifier.verifyTextInLog("No KSP processors found in dependencies")
    }

    @Test
    fun `should skip KSP processing when skip property is set`() {
        // Given
        val testProject = createTestProject("skip-project")

        // When
        val verifier = Verifier(testProject.absolutePath)
        verifier.isAutoclean = false
        verifier.setLocalRepo(localRepoPath)
        verifier.addCliArgument("-Dksp.skip=true")
        verifier.addCliArgument("clean")
        verifier.addCliArgument("compile")
        verifier.execute()

        // Then
        verifier.verifyErrorFreeLog()
        verifier.verifyTextInLog("Skipping KSP processing")

        // Verify no generated files
        val generatedDir = testProject.resolve("target/generated-sources/ksp")
        assertThat(generatedDir).doesNotExist()
    }

    @Test
    fun `should process multiple source files`() {
        // Given
        val testProject = createTestProject("multi-source-project", multipleFiles = true)

        // When
        val verifier = Verifier(testProject.absolutePath)
        verifier.isAutoclean = false
        verifier.setLocalRepo(localRepoPath)
        verifier.addCliArgument("clean")
        verifier.addCliArgument("compile")
        verifier.execute()

        // Then
        verifier.verifyErrorFreeLog()

        // Verify multiple generated files exist
        val generatedFile1 = testProject.resolve(
            "target/generated-sources/ksp/com/example/FirstClassGreeting.kt"
        )
        val generatedFile2 = testProject.resolve(
            "target/generated-sources/ksp/com/example/SecondClassGreeting.kt"
        )

        assertThat(generatedFile1).exists()
        assertThat(generatedFile2).exists()

        assertThat(generatedFile1.readText()).contains("Hello, First!")
        assertThat(generatedFile2.readText()).contains("Hello, Second!")
    }

    @Test
    fun `should handle custom processor options`() {
        // Given
        val testProject = createTestProject("custom-options-project", withOptions = true)

        // When
        val verifier = Verifier(testProject.absolutePath)
        verifier.isAutoclean = false
        verifier.setLocalRepo(localRepoPath)
        verifier.addCliArgument("clean")
        verifier.addCliArgument("compile")
        verifier.execute()

        // Then
        verifier.verifyErrorFreeLog()
    }

    /**
     * Creates a test Maven project with KSP configuration
     */
    private fun createTestProject(
        projectName: String,
        includeProcessor: Boolean = true,
        multipleFiles: Boolean = false,
        withOptions: Boolean = false
    ): File {
        val projectDir = testProjectsDir.resolve(projectName).toFile()
        projectDir.mkdirs()

        // Create pom.xml
        createPomXml(projectDir, includeProcessor, withOptions)

        // Create source directory
        val srcDir = projectDir.resolve("src/main/kotlin/com/example")
        srcDir.mkdirs()

        // Create source files only if processor is included
        if (includeProcessor) {
            if (multipleFiles) {
                createSourceFile(srcDir, "FirstClass", "First")
                createSourceFile(srcDir, "SecondClass", "Second")
            } else {
                createSourceFile(srcDir, "TestClass", "Integration Test")
            }
        } else {
            // Create a simple source file without annotations
            createSimpleSourceFile(srcDir, "TestClass")
        }

        return projectDir
    }

    private fun createPomXml(
        projectDir: File,
        includeProcessor: Boolean,
        withOptions: Boolean
    ) {
        val processorDependency = if (includeProcessor) {
            """
            <dependency>
                <groupId>me.kpavlov.ksp.maven</groupId>
                <artifactId>ksp-maven-plugin</artifactId>
                <version>$pluginVersion</version>
                <classifier>tests</classifier>
            </dependency>
            """
        } else {
            ""
        }
        // language=xml
        val pluginOptions = if (withOptions) {
            """
            <configuration>
                <apOptions>
                    <customOption>customValue</customOption>
                </apOptions>
            </configuration>
            """
        } else {
            ""
        }

        // language=xml
        val pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>

                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0-SNAPSHOT</version>

                <properties>
                    <maven.compiler.source>11</maven.compiler.source>
                    <maven.compiler.target>11</maven.compiler.target>
                    <kotlin.version>$kotlinVersion</kotlin.version>
                    <ksp.version>$kspVersion</ksp.version>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>${"$"}{kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>com.google.devtools.ksp</groupId>
                        <artifactId>symbol-processing-api</artifactId>
                        <version>${"$"}{ksp.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>com.google.devtools.ksp</groupId>
                        <artifactId>symbol-processing-aa-embeddable</artifactId>
                        <version>${"$"}{ksp.version}</version>
                    </dependency>
                    $processorDependency
                </dependencies>

                <build>
                    <sourceDirectory>src/main/kotlin</sourceDirectory>
                    <plugins>
                        <plugin>
                            <groupId>me.kpavlov.ksp.maven</groupId>
                            <artifactId>ksp-maven-plugin</artifactId>
                            <version>$pluginVersion</version>
                            $pluginOptions
                            <executions>
                                <execution>
                                    <goals>
                                        <goal>process</goal>
                                    </goals>
                                </execution>
                            </executions>
                            <dependencies>
                                <dependency>
                                    <groupId>com.google.devtools.ksp</groupId>
                                    <artifactId>symbol-processing-api</artifactId>
                                    <version>${"$"}{ksp.version}</version>
                                </dependency>
                                $processorDependency
                            </dependencies>
                        </plugin>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <version>${"$"}{kotlin.version}</version>
                            <executions>
                                <execution>
                                    <id>compile</id>
                                    <phase>compile</phase>
                                    <goals>
                                        <goal>compile</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
            </project>
        """.trimIndent()

        projectDir.resolve("pom.xml").writeText(pomContent)
    }

    private fun createSourceFile(srcDir: File, className: String, greetingName: String) {
        val sourceContent = """
            package com.example

            import me.kpavlov.ksp.maven.testprocessor.GenerateHello

            @GenerateHello(name = "$greetingName")
            class $className {
                fun sayHello() = println("Hello from $className")
            }
        """.trimIndent()

        srcDir.resolve("$className.kt").writeText(sourceContent)
    }

    private fun createSimpleSourceFile(srcDir: File, className: String) {
        val sourceContent = """
            package com.example

            class $className {
                fun sayHello() = println("Hello from $className")
            }
        """.trimIndent()

        srcDir.resolve("$className.kt").writeText(sourceContent)
    }
}
