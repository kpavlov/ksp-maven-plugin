package me.kpavlov.ksp.maven

import io.kotest.matchers.shouldBe
import org.apache.maven.model.Build
import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junitpioneer.jupiter.Issue
import java.io.File

@Issue("https://github.com/kpavlov/ksp-maven-plugin/issues/44")
class KspParameterDetectionTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("languageVersionData")
    fun `should detect languageVersion`(
        description: String,
        project: MavenProject,
        explicit: String?,
        expected: String,
    ) {
        detectLanguageVersion(project, explicit) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jvmTargetData")
    fun `should detect jvmTarget`(
        description: String,
        project: MavenProject,
        explicit: String?,
        expected: String,
    ) {
        detectJvmTarget(project, explicit) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jdkHomeData")
    fun `should detect jdkHome`(
        description: String,
        project: MavenProject,
        expectedPath: String?,
    ) {
        val detected = detectJdkHome(project)
        if (expectedPath != null) {
            detected.path shouldBe expectedPath
        } else {
            detected.path shouldBe System.getProperty("java.home")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allWarningsAsErrorsData")
    fun `should detect allWarningsAsErrors`(
        description: String,
        project: MavenProject,
        explicit: Boolean,
        expected: Boolean,
    ) {
        detectAllWarningsAsErrors(project, explicit) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun languageVersionData() =
            listOf(
                Arguments.of(
                    "languageVersion from configuration",
                    createProject(kotlinPluginConfig = mapOf("languageVersion" to "2.1")),
                    null,
                    "2.1",
                ),
                Arguments.of(
                    "languageVersion from properties",
                    createProject(properties = mapOf("kotlin.compiler.languageVersion" to "2.0")),
                    null,
                    "2.0",
                ),
                Arguments.of(
                    "languageVersion from kotlin.version property",
                    createProject(properties = mapOf("kotlin.version" to "2.3.5")),
                    null,
                    "2.3",
                ),
                Arguments.of(
                    "languageVersion from short kotlin.version property",
                    createProject(properties = mapOf("kotlin.version" to "2.2")),
                    null,
                    "2.2",
                ),
                Arguments.of("languageVersion fallback to default", createProject(), null, "2.2"),
                Arguments.of("languageVersion explicit", createProject(), "1.9", "1.9"),
            )

        @JvmStatic
        fun jvmTargetData() =
            listOf(
                Arguments.of(
                    "jvmTarget from configuration",
                    createProject(kotlinPluginConfig = mapOf("jvmTarget" to "17")),
                    null,
                    "17",
                ),
                Arguments.of(
                    "jvmTarget from kotlin compiler property",
                    createProject(properties = mapOf("kotlin.compiler.jvmTarget" to "21")),
                    null,
                    "21",
                ),
                Arguments.of(
                    "jvmTarget from maven compiler release property",
                    createProject(properties = mapOf("maven.compiler.release" to "25")),
                    null,
                    "25",
                ),
                Arguments.of(
                    "jvmTarget from maven compiler target property",
                    createProject(properties = mapOf("maven.compiler.target" to "11")),
                    null,
                    "11",
                ),
                Arguments.of("jvmTarget fallback to default", createProject(), null, "11"),
                Arguments.of("jvmTarget explicit", createProject(), "1.7", "1.7"),
            )

        @JvmStatic
        fun jdkHomeData() =
            listOf(
                Arguments.of(
                    "jdkHome from configuration",
                    createProject(kotlinPluginConfig = mapOf("jdkHome" to "/path/to/jdk")),
                    "/path/to/jdk",
                ),
                Arguments.of(
                    "jdkHome from properties",
                    createProject(properties = mapOf("kotlin.compiler.jdkHome" to "/another/path")),
                    "/another/path",
                ),
                Arguments.of("jdkHome fallback to java.home", createProject(), null),
            )

        @JvmStatic
        fun allWarningsAsErrorsData() =
            listOf(
                Arguments.of(
                    "allWarningsAsErrors from properties (true)",
                    createProject(
                        properties = mapOf("kotlin.compiler.allWarningsAsErrors" to "true"),
                    ),
                    false,
                    true,
                ),
                Arguments.of(
                    "allWarningsAsErrors from properties (false)",
                    createProject(
                        properties =
                            mapOf("kotlin.compiler.allWarningsAsErrors" to "false"),
                    ),
                    false,
                    false,
                ),
                Arguments.of(
                    "allWarningsAsErrors from configuration (true)",
                    createProject(kotlinPluginConfig = mapOf("allWarningsAsErrors" to "true")),
                    false,
                    true,
                ),
                Arguments.of("allWarningsAsErrors explicitly true", createProject(), true, true),
                Arguments.of(
                    "allWarningsAsErrors fallback to false",
                    createProject(),
                    false,
                    false,
                ),
            )

        private fun createProject(
            properties: Map<String, String> = emptyMap(),
            kotlinPluginConfig: Map<String, String> = emptyMap(),
        ): MavenProject {
            val project = MavenProject()
            properties.forEach { (k, v) -> project.properties.setProperty(k, v) }
            if (kotlinPluginConfig.isNotEmpty()) {
                val build = Build()
                val plugin =
                    Plugin().apply {
                        groupId = "org.jetbrains.kotlin"
                        artifactId = "kotlin-maven-plugin"
                        configuration =
                            Xpp3Dom("configuration").apply {
                                kotlinPluginConfig.forEach { (k, v) ->
                                    addChild(Xpp3Dom(k).apply { value = v })
                                }
                            }
                    }
                build.addPlugin(plugin)
                project.build = build
            }
            return project
        }
    }

    private fun detectLanguageVersion(
        project: MavenProject,
        explicit: String?,
    ): String {
        if (explicit != null) return explicit
        val kotlinPlugin =
            project.build?.pluginsAsMap?.get("org.jetbrains.kotlin:kotlin-maven-plugin")
        val config = kotlinPlugin?.configuration as? Xpp3Dom
        return config?.getChild("languageVersion")?.value
            ?: project.properties.getProperty("kotlin.compiler.languageVersion")
            ?: project.properties.getProperty("kotlin.version")?.let { extractMajorMinor(it) }
            ?: "2.2"
    }

    private fun detectJvmTarget(
        project: MavenProject,
        explicit: String?,
    ): String {
        if (explicit != null) return explicit
        val kotlinPlugin =
            project.build?.pluginsAsMap?.get("org.jetbrains.kotlin:kotlin-maven-plugin")
        val config = kotlinPlugin?.configuration as? Xpp3Dom
        return config?.getChild("jvmTarget")?.value
            ?: project.properties.getProperty("kotlin.compiler.jvmTarget")
            ?: project.properties.getProperty("maven.compiler.release")
            ?: project.properties.getProperty("maven.compiler.target")
            ?: "11"
    }

    private fun detectJdkHome(project: MavenProject): File {
        val kotlinPlugin =
            project.build?.pluginsAsMap?.get("org.jetbrains.kotlin:kotlin-maven-plugin")
        val config = kotlinPlugin?.configuration as? Xpp3Dom
        val path =
            config?.getChild("jdkHome")?.value
                ?: project.properties.getProperty("kotlin.compiler.jdkHome")
        return if (path != null) File(path) else File(System.getProperty("java.home"))
    }

    private fun detectAllWarningsAsErrors(
        project: MavenProject,
        explicit: Boolean,
    ): Boolean {
        val kotlinPlugin =
            project.build?.pluginsAsMap?.get("org.jetbrains.kotlin:kotlin-maven-plugin")
        val config = kotlinPlugin?.configuration as? Xpp3Dom
        return explicit ||
            config?.getChild("allWarningsAsErrors")?.value?.toBoolean() == true ||
            project.properties
                .getProperty("kotlin.compiler.allWarningsAsErrors")
                ?.toBoolean() == true
    }

    private fun extractMajorMinor(version: String): String {
        val parts = version.split(".")
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
    }
}
