package me.kpavlov.ksp.maven

import org.apache.maven.AbstractMavenLifecycleParticipant
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.codehaus.plexus.util.xml.Xpp3Dom
import javax.inject.Named
import javax.inject.Singleton

@Named
@Singleton
class KspLifecycleParticipant : AbstractMavenLifecycleParticipant() {

    override fun afterProjectsRead(session: MavenSession) {
        session.projects.forEach { project ->
            val plugin = project.build.pluginsAsMap["me.kpavlov.ksp.maven:ksp-maven-plugin"]
            if (plugin != null && plugin.isExtensions) {
                addExecution(plugin, "process-main-sources", "process", "generate-sources")
                addExecution(plugin, "process-test-sources", "process-test", "generate-test-sources")
            }
        }
    }

    private fun addExecution(
        plugin: Plugin,
        id: String,
        goal: String,
        phase: String,
    ) {
        if (plugin.executionsAsMap.containsKey(id)) {
            return
        }

        val execution = PluginExecution().apply {
            this.id = id
            this.phase = phase
            this.goals = listOf(goal)
            // Propagate plugin-level <configuration> so that user-configured parameters
            // (<processorIncludes>, <processorExcludes>, <debug>, etc.) are applied to
            // auto-bound executions that Maven would otherwise leave unconfigured.
            (plugin.configuration as? Xpp3Dom)?.let { pluginConfig ->
                this.configuration = Xpp3Dom(pluginConfig)
            }
        }
        plugin.addExecution(execution)
    }
}
