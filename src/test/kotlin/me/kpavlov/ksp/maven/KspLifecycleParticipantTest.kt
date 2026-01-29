package me.kpavlov.ksp.maven

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Build
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.apache.maven.project.MavenProject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class KspLifecycleParticipantTest {

    private lateinit var participant: KspLifecycleParticipant
    private lateinit var session: MavenSession
    private lateinit var project: MavenProject
    private lateinit var plugin: Plugin

    @BeforeEach
    fun setUp() {
        participant = KspLifecycleParticipant()
        session = mock(MavenSession::class.java)
        project = mock(MavenProject::class.java)
        val build = mock(Build::class.java)
        plugin = Plugin()
        plugin.groupId = "me.kpavlov.ksp.maven"
        plugin.artifactId = "ksp-maven-plugin"

        `when`(session.projects).thenReturn(listOf(project))
        `when`(project.build).thenReturn(build)
        `when`(build.pluginsAsMap).thenReturn(mapOf("me.kpavlov.ksp.maven:ksp-maven-plugin" to plugin))
    }

    @Test
    fun `should add executions when extensions is true`() {
        plugin.isExtensions = true

        participant.afterProjectsRead(session)

        val executions = plugin.executions
        executions shouldHaveSize 2

        val mainExecution = executions.find { it.id == "process-main-sources" }
        mainExecution shouldNotBeNull {
            assertSoftly {
                phase shouldBe "generate-sources"
                goals shouldBe listOf("process")
            }
        }

        val testExecution = executions.find { it.id == "process-test-sources" }
        testExecution shouldNotBeNull {
            assertSoftly {
                phase shouldBe "generate-test-sources"
                goals shouldBe listOf("process-test")
            }
        }
    }

    @Test
    fun `should not add executions when extensions is false`() {
        plugin.isExtensions = false

        participant.afterProjectsRead(session)

        plugin.executions.shouldBeEmpty()
    }

    @Test
    fun `should not overwrite existing executions`() {
        plugin.isExtensions = true
        val existingExecution = PluginExecution()
        existingExecution.id = "process-main-sources"
        plugin.addExecution(existingExecution)

        participant.afterProjectsRead(session)

        val executions = plugin.executions
        executions shouldHaveSize 2
        executions shouldContain existingExecution
        executions.any { it.id == "process-test-sources" } shouldBe true
    }
}
