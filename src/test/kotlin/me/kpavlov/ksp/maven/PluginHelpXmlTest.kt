package me.kpavlov.ksp.maven

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class PluginHelpXmlTest {
    @Test
    fun `plugin-help xml should contain mojo descriptions`() {
        val doc = parseXml()
        val mojos = doc.getElementsByTagName("mojo")

        withClue("plugin-help.xml should contain at least one mojo") {
            mojos.length shouldNotBe 0
        }

        val mojoGoals = mutableListOf<String>()
        for (i in 0 until mojos.length) {
            val mojo = mojos.item(i) as Element
            val goal = mojo.getElementText("goal")
            val description = mojo.getElementText("description")

            mojoGoals.add(goal)

            withClue("Mojo '$goal' should have a non-blank description") {
                description.shouldNotBeBlank()
            }
        }

        mojoGoals shouldContainAll listOf("process", "process-test")
    }

    @ParameterizedTest(name = "plugin-help xml should contain parameter descriptions for {0} goal")
    @ValueSource(strings = ["process", "process-test"])
    fun `plugin-help xml should contain parameter descriptions`(goalName: String) {
        val doc = parseXml()
        val mojos = doc.getElementsByTagName("mojo")

        val expectedParameters =
            when (goalName) {
                "process" -> EXPECTED_PROCESS_PARAMETERS
                "process-test" -> EXPECTED_PROCESS_TEST_PARAMETERS
                else -> error("Unknown goal: $goalName")
            }

        var mojoFound = false
        for (i in 0 until mojos.length) {
            val mojo = mojos.item(i) as Element
            val goal = mojo.getElementText("goal")

            if (goal == goalName) {
                mojoFound = true
                verifyMojoParameters(mojo, goal, expectedParameters)
                break
            }
        }

        withClue("'$goalName' mojo should be present in plugin-help.xml") {
            mojoFound shouldBe true
        }
    }

    @Test
    fun `plugin-help xml parameters should have since tags`() {
        val doc = parseXml()
        val parameters = doc.getElementsByTagName("parameter")

        withClue("plugin-help.xml should have parameters") {
            parameters.length shouldNotBe 0
        }

        val parametersWithSince = mutableListOf<String>()
        for (i in 0 until parameters.length) {
            val param = parameters.item(i) as Element
            val name = param.getElementText("name")
            val since = param.getElementTextOrNull("since")

            if (!since.isNullOrBlank()) {
                parametersWithSince.add(name)
            }
        }

        withClue("At least some parameters should have @since tags") {
            parametersWithSince.shouldNotBeEmpty()
        }
    }

    private fun verifyMojoParameters(
        mojo: Element,
        goal: String,
        expectedParameters: List<String>,
    ) {
        val parameters = mojo.getElementsByTagName("parameter")

        withClue("Mojo '$goal' should have parameters") {
            parameters.length shouldNotBe 0
        }

        val foundParameters = mutableListOf<String>()
        val parametersWithDescriptions = mutableListOf<String>()

        for (i in 0 until parameters.length) {
            val param = parameters.item(i) as Element
            val name = param.getElementText("name")
            val description = param.getElementTextOrNull("description")

            foundParameters.add(name)

            if (!description.isNullOrBlank()) {
                parametersWithDescriptions.add(name)
            }
        }

        withClue("Mojo '$goal' should contain expected parameters") {
            foundParameters shouldContainAll expectedParameters
        }

        withClue("Mojo '$goal' parameters should have descriptions") {
            parametersWithDescriptions.shouldNotBeEmpty()
        }
    }

    companion object {
        private const val PLUGIN_HELP_XML_PATH =
            "META-INF/maven/me.kpavlov.ksp.maven/ksp-maven-plugin/plugin-help.xml"

        /** Expected parameters for the 'process' goal (main sources). */
        private val EXPECTED_PROCESS_PARAMETERS =
            listOf(
                "skip",
                "sourceDirectory",
                "jvmTarget",
                "languageVersion",
                "apiVersion",
                "moduleName",
                "processorOptions",
                "kotlinOutputDir",
                "javaOutputDir",
                "classOutputDir",
                "resourceOutputDir",
            )

        /** Expected parameters for the 'process-test' goal (test sources). */
        private val EXPECTED_PROCESS_TEST_PARAMETERS =
            listOf(
                "skipTest",
                "sourceDirectory",
                "jvmTarget",
                "languageVersion",
                "apiVersion",
                "moduleName",
                "processorOptions",
                "kotlinOutputDir",
                "javaOutputDir",
                "classOutputDir",
                "resourceOutputDir",
            )
    }

    private fun getPluginHelpXmlResource() = javaClass.classLoader.getResource(PLUGIN_HELP_XML_PATH)

    private fun parseXml(): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val resource =
            getPluginHelpXmlResource()
                ?: error("plugin-help.xml not found on classpath at $PLUGIN_HELP_XML_PATH")
        return resource.openStream().use { builder.parse(it) }
    }

    private fun Element.getElementText(tagName: String): String {
        val nodes = getElementsByTagName(tagName)
        return if (nodes.length > 0) nodes.item(0).textContent.trim() else ""
    }

    private fun Element.getElementTextOrNull(tagName: String): String? {
        val nodes = getElementsByTagName(tagName)
        return if (nodes.length > 0) nodes.item(0).textContent.trim() else null
    }
}
