package me.kpavlov.ksp.maven

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.apache.maven.plugin.logging.Log

/**
 * Filters a list of [SymbolProcessorProvider] instances using glob-style patterns
 * applied to their fully-qualified class names.
 *
 * - If [includes] is empty, all providers pass the include check by default.
 * - A provider is retained only when it satisfies the include check **and** does not
 *   match any pattern in [excludes].
 *
 * Pattern syntax:
 * - `*` matches any sequence of characters within a single package segment (no dots)
 * - `**` matches any sequence of characters across package segments (including dots)
 * - `?` matches any single non-dot character
 * - All other characters are matched literally
 *
 * Examples:
 * - `com.example.MyProcessor` — exact class match
 * - `com.example.*` — all providers directly in the `com.example` package
 * - `com.example.**` — all providers in `com.example` and any sub-package
 *
 * @param providers the full list of discovered providers. Providers whose
 *   `provider::class.qualifiedName` is `null` (e.g. anonymous object expressions)
 *   are always excluded when any [includes] or [excludes] filter is active,
 *   because they cannot be matched against a pattern.
 * @param includes glob patterns for classes to include; empty means include all
 * @param excludes glob patterns for classes to exclude; applied after includes
 * @param log optional sink for per-provider exclusion messages; called once for
 *   each provider that is dropped, with a human-readable reason
 * @return the filtered list, in the same order as [providers]. When both [includes]
 *   and [excludes] are empty the original list is returned unchanged and no provider
 *   is excluded regardless of its `qualifiedName`.
 */
internal fun filterProcessorProviders(
    providers: List<SymbolProcessorProvider>,
    includes: List<String>,
    excludes: List<String>,
    log: Log,
): List<SymbolProcessorProvider> {
    if (includes.isEmpty() && excludes.isEmpty()) return providers

    // Compile each pattern once, not once per provider × pattern
    val includeRegexes = includes.map(::buildGlobRegex)
    val excludeRegexes = excludes.map(::buildGlobRegex)

    return providers.filter { provider ->
        val className = provider::class.qualifiedName
        if (className == null) {
            log.info(
                "Excluding provider ${provider::class} — qualifiedName is null, " +
                    "cannot match against patterns",
            )
            return@filter false
        }
        val included = includeRegexes.isEmpty() || includeRegexes.any { it.matches(className) }
        val excluded = excludeRegexes.any { it.matches(className) }
        val retained = included && !excluded
        if (!retained) {
            val reason =
                if (!included) {
                    "not matched by any include pattern: $includes"
                } else {
                    "matched by exclude pattern in: $excludes"
                }
            log.debug("Excluding provider $className — $reason")
        }
        retained
    }
}

/**
 * Returns `true` if [text] matches the glob [pattern].
 *
 * - `*` matches any sequence of non-dot characters
 * - `**` matches any sequence of characters (including dots)
 * - `?` matches any single non-dot character
 * - All other characters in [pattern] are matched literally
 */
internal fun matchesGlob(
    text: String,
    pattern: String,
): Boolean = buildGlobRegex(pattern).matches(text)

// Tokenises a glob pattern into: "**", "*", "?", or a literal run — in that alternation order
// so "**" is always matched before "*".
private val globTokenRegex = Regex("""\*\*|\*|\?|[^*?]+""")

private fun buildGlobRegex(pattern: String): Regex =
    globTokenRegex
        .findAll(pattern)
        .joinToString(separator = "", prefix = "^", postfix = "$") { token ->
            when (token.value) {
                "**" -> ".*"
                "*" -> "[^.]*"
                "?" -> "[^.]"
                else -> Regex.escape(token.value)
            }
        }.let(::Regex)
