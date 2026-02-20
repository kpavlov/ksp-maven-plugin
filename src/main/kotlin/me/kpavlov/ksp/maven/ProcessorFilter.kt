package me.kpavlov.ksp.maven

import com.google.devtools.ksp.processing.SymbolProcessorProvider

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
 * @param providers the full list of discovered providers
 * @param includes glob patterns for classes to include; empty means include all
 * @param excludes glob patterns for classes to exclude; applied after includes
 * @return the filtered list, in the same order as [providers]
 */
internal fun filterProcessorProviders(
    providers: List<SymbolProcessorProvider>,
    includes: List<String>,
    excludes: List<String>,
): List<SymbolProcessorProvider> {
    if (includes.isEmpty() && excludes.isEmpty()) return providers

    return providers.filter { provider ->
        val className = provider::class.qualifiedName ?: return@filter false
        val included = includes.isEmpty() || includes.any { matchesGlob(className, it) }
        val excluded = excludes.any { matchesGlob(className, it) }
        included && !excluded
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

private fun buildGlobRegex(pattern: String): Regex {
    val sb = StringBuilder("^")
    var i = 0
    while (i < pattern.length) {
        when {
            i + 1 < pattern.length && pattern[i] == '*' && pattern[i + 1] == '*' -> {
                sb.append(".*")
                i += 2
            }

            pattern[i] == '*' -> {
                sb.append("[^.]*")
                i++
            }

            pattern[i] == '?' -> {
                sb.append("[^.]")
                i++
            }

            else -> {
                sb.append(Regex.escape(pattern[i].toString()))
                i++
            }
        }
    }
    sb.append("$")
    return Regex(sb.toString())
}
