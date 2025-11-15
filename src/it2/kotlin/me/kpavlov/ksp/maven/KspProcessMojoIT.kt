package me.kpavlov.ksp.maven

import org.apache.maven.shared.verifier.Verifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Integration tests for KSP Maven Plugin using Maven Verifier.
 * Tests run against pre-configured projects in test-projects/ directory.
 */
@Disabled
class KspProcessMojoIT {

    companion object {
        private lateinit var localRepoPath: String
        private lateinit var testProjectsDir: File

        @JvmStatic
        @BeforeAll
        fun setup() {
            val projectDir = System.getProperty("user.dir")
            localRepoPath = "$projectDir/target/local-repo"
            testProjectsDir = File("$projectDir/target/test-projects")

            require(testProjectsDir.exists()) {
                "Test projects directory not found at ${testProjectsDir.absolutePath}. " +
                        "Make sure maven-resources-plugin has copied test projects."
            }
        }
    }

    @Test
    fun `simple project should generate code and tests should pass`() {
        // Given
        val projectDir = File(testProjectsDir, "simple-project")
        require(projectDir.exists()) { "simple-project not found at ${projectDir.absolutePath}" }

        // When
        val verifier = Verifier(projectDir.absolutePath)
        verifier.isAutoclean = false
        verifier.setLocalRepo(localRepoPath)
        verifier.addCliArgument("clean")
        verifier.addCliArgument("test")
        verifier.execute()

        // Then
        verifier.verifyErrorFreeLog()
        verifier.verifyTextInLog("BUILD SUCCESS")

        // Verify generated file exists
        val generatedFile = projectDir.resolve(
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

        // Verify tests passed (they use the generated code)
        verifier.verifyTextInLog("Tests run:")
        verifier.verifyTextInLog("Failures: 0")
        verifier.verifyTextInLog("Errors: 0")
    }
}
