package me.kpavlov.ksp.maven

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// Top-level classes so their qualifiedName is well-defined and predictable
// (me.kpavlov.ksp.maven.AlphaOneFakeProvider, etc.)
private class AlphaOneFakeProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        error("not for execution")
}

private class AlphaTwoFakeProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        error("not for execution")
}

private class BetaFakeProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        error("not for execution")
}

class ProcessorFilterTest {
    // Qualified names under test – keep in sync with class declarations above
    private val alphaOneFqcn = "me.kpavlov.ksp.maven.AlphaOneFakeProvider"
    private val alphaTwoFqcn = "me.kpavlov.ksp.maven.AlphaTwoFakeProvider"
    private val betaFqcn = "me.kpavlov.ksp.maven.BetaFakeProvider"

    private val alphaOne: SymbolProcessorProvider = AlphaOneFakeProvider()
    private val alphaTwo: SymbolProcessorProvider = AlphaTwoFakeProvider()
    private val beta: SymbolProcessorProvider = BetaFakeProvider()

    private val allProviders = listOf(alphaOne, alphaTwo, beta)

    // Verify that the class names we rely on are actually what Kotlin reports
    @Test
    fun `verify fixture class names are as expected`() {
        alphaOne::class.qualifiedName shouldBe alphaOneFqcn
        alphaTwo::class.qualifiedName shouldBe alphaTwoFqcn
        beta::class.qualifiedName shouldBe betaFqcn
    }

    // ── matchesGlob ─────────────────────────────────────────────────────────

    @Nested
    inner class MatchesGlobTest {
        @ParameterizedTest(name = "\"{0}\" matches \"{1}\" => {2}")
        @CsvSource(
            "com.example.Foo,       com.example.Foo,    true",
            "com.example.Foo,       com.example.Bar,    false",
            "com.example.Foo,       com.example.*,      true",
            "com.example.sub.Foo,   com.example.*,      false",
            "com.example.sub.Foo,   com.example.**,     true",
            "com.example.a.b.Foo,   com.example.**,     true",
            "com.example.Foo,       **,                 true",
            "com.acme.Foo,          com.example.*,      false",
            "com.example.FooBar,    com.example.Foo*,   true",
            "com.example.FooBar,    com.example.*Bar,   true",
            "com.example.FooBar,    com.example.Foo?,   false",
            "com.example.FooBa,     com.example.Foo?a,  true",
        )
        fun `matchesGlob produces correct results`(
            text: String,
            pattern: String,
            expected: Boolean,
        ) {
            matchesGlob(text.trim(), pattern.trim()) shouldBe expected
        }

        @Test
        fun `single-star does not cross package boundary`() {
            matchesGlob("com.acme.sub.Foo", "com.acme.*") shouldBe false
        }

        @Test
        fun `double-star crosses package boundaries`() {
            matchesGlob("com.acme.sub.deep.Foo", "com.acme.**") shouldBe true
        }

        @Test
        fun `dot in pattern is literal, not any-character`() {
            matchesGlob("comXexampleYFoo", "com.example.Foo") shouldBe false
        }
    }

    // ── filterProcessorProviders ────────────────────────────────────────────

    @Nested
    inner class FilterProcessorProvidersTest {
        private val pkg = "me.kpavlov.ksp.maven"

        @Test
        fun `returns all providers when both filter lists are empty`() {
            val result = filterProcessorProviders(allProviders, emptyList(), emptyList())
            result shouldContainExactly allProviders
        }

        @Test
        fun `includes only providers matching the include pattern`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = listOf("$pkg.Alpha*"),
                    excludes = emptyList(),
                )
            result shouldContainExactly listOf(alphaOne, alphaTwo)
        }

        @Test
        fun `exact include match selects a single provider`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = listOf(betaFqcn),
                    excludes = emptyList(),
                )
            result shouldContainExactly listOf(beta)
        }

        @Test
        fun `excludes remove matching providers from full list`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = emptyList(),
                    excludes = listOf("$pkg.Alpha*"),
                )
            result shouldContainExactly listOf(beta)
        }

        @Test
        fun `excludes override includes when both patterns match`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = listOf("$pkg.Alpha*"),
                    excludes = listOf("$pkg.AlphaTwo*"),
                )
            // alphaOne: included, not excluded  => kept
            // alphaTwo: included AND excluded    => removed
            // beta:     not included             => removed
            result shouldContainExactly listOf(alphaOne)
        }

        @Test
        fun `returns empty list when no providers match the include pattern`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = listOf("org.unrelated.*"),
                    excludes = emptyList(),
                )
            result.shouldBeEmpty()
        }

        @Test
        fun `double-star exclude removes all providers`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = emptyList(),
                    excludes = listOf("**"),
                )
            result.shouldBeEmpty()
        }

        @Test
        fun `multiple include patterns act as a union`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = listOf("$pkg.AlphaOne*", "$pkg.Beta*"),
                    excludes = emptyList(),
                )
            result shouldContainExactly listOf(alphaOne, beta)
        }

        @Test
        fun `multiple exclude patterns act as a union`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = emptyList(),
                    excludes = listOf("$pkg.AlphaOne*", "$pkg.Beta*"),
                )
            result shouldContainExactly listOf(alphaTwo)
        }

        @Test
        fun `provider with null qualifiedName is excluded when filtering is active`() {
            // Anonymous object expressions have qualifiedName == null in Kotlin
            val anonymous =
                object : SymbolProcessorProvider {
                    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
                        error("not for execution")
                }
            val providers = listOf(alphaOne, anonymous)

            val result =
                filterProcessorProviders(
                    providers,
                    includes = listOf("$pkg.Alpha*"),
                    excludes = emptyList(),
                )

            result shouldHaveSize 1
            result[0] shouldBe alphaOne
        }

        @Test
        fun `provider with null qualifiedName passes through when no filters are set`() {
            val anonymous =
                object : SymbolProcessorProvider {
                    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
                        error("not for execution")
                }
            val providers = listOf(alphaOne, anonymous)

            val result = filterProcessorProviders(providers, emptyList(), emptyList())

            // No filtering => original list returned as-is
            result shouldContainExactly providers
        }
    }
}
