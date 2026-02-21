package me.kpavlov.ksp.maven

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.apache.maven.plugin.logging.Log
import org.apache.maven.plugin.testing.SilentLog
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
            // single-star does not cross the package boundary
            "com.example.sub.Foo,   com.example.*,      false",
            "com.example.sub.Foo,   com.example.**,     true",
            // double-star crosses multiple segments
            "com.example.a.b.Foo,   com.example.**,     true",
            "com.example.Foo,       **,                 true",
            "com.acme.Foo,          com.example.*,      false",
            "com.example.FooBar,    com.example.Foo*,   true",
            "com.example.FooBar,    com.example.*Bar,   true",
            "com.example.FooBar,    com.example.Foo?,   false",
            "com.example.FooBa,     com.example.Foo?a,  true",
            // dot in a pattern is literal, not any-character
            "comXexampleYFoo,       com.example.Foo,    false",
        )
        fun `matchesGlob produces correct results`(
            text: String,
            pattern: String,
            expected: Boolean,
        ) {
            matchesGlob(text.trim(), pattern.trim()) shouldBe expected
        }
    }

    // ── filterProcessorProviders ────────────────────────────────────────────

    @Nested
    inner class FilterProcessorProvidersTest {
        private val pkg = "me.kpavlov.ksp.maven"
        private val log: Log = SilentLog()

        @Test
        fun `returns all providers when both filter lists are empty`() {
            val result =
                filterProcessorProviders(
                    providers = allProviders,
                    includes = emptyList(),
                    excludes = emptyList(),
                    log = log,
                )
            result shouldContainExactly allProviders
        }

        @Test
        fun `includes only providers matching the include pattern`() {
            val result =
                filterProcessorProviders(
                    allProviders,
                    includes = listOf("$pkg.Alpha*"),
                    excludes = emptyList(),
                    log = log,
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
                    log = log,
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
                    log = log,
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
                    log = log,
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
                    log = log,
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
                    log = log,
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
                    log = log,
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
                    log = log,
                )
            result shouldContainExactly listOf(alphaTwo)
        }

        @Test
        fun `provider with null qualifiedName is excluded when filtering is active`() {
            // Anonymous object expressions have qualifiedName == null in Kotlin
            val anonymous = SymbolProcessorProvider { error("not for execution") }
            val providers = listOf(alphaOne, anonymous)

            val result =
                filterProcessorProviders(
                    providers,
                    includes = listOf("$pkg.Alpha*"),
                    excludes = emptyList(),
                    log = log,
                )

            result shouldContainExactly listOf(alphaOne)
        }

        @Test
        fun `provider with null qualifiedName passes through when no filters are set`() {
            val anonymous =
                object : SymbolProcessorProvider {
                    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
                        error("not for execution")
                }
            val providers = listOf(alphaOne, anonymous)

            val result =
                filterProcessorProviders(
                    providers = providers,
                    includes = emptyList(),
                    excludes = emptyList(),
                    log = log,
                )

            // No filtering => original list returned as-is
            result shouldContainExactly providers
        }
    }
}
