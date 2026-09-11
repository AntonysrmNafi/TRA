package com.blockveil.tracker.remover.cleaner

import java.net.URI
import java.net.URLDecoder

/**
 * Pure, offline link cleaning: no network calls in this file. Finding a
 * link inside pasted/shared text, then stripping known tracking
 * parameters from it, is the one thing this app should always be able
 * to do even with no internet connection.
 */
object LinkCleaner {

    private val URL_REGEX = Regex(
        """(https?://[^\s<>"']+)""",
        RegexOption.IGNORE_CASE
    )

    data class CleanedUrl(
        val original: String,
        val cleaned: String,
        val removedParams: List<String>,
        val domain: String,
        val isShortener: Boolean
    )

    /** Pulls out the first http(s) URL found in free-form text, or null. */
    fun extractFirstUrl(text: String): String? {
        val match = URL_REGEX.find(text.trim()) ?: return null
        return stripTrailingPunctuation(match.value)
    }

    /** Some chat apps leave trailing punctuation stuck to a pasted URL. */
    private fun stripTrailingPunctuation(url: String): String {
        var result = url
        val trailing = charArrayOf('.', ',', ')', ']', '}', '!', '?', ';', '"', '\'')
        while (result.isNotEmpty() && result.last() in trailing) {
            // Keep a balanced closing paren, e.g. Wikipedia URLs like
            // ...Kotlin_(programming_language) should not lose their ')'.
            if (result.last() == ')' && result.count { it == '(' } >= result.count { it == ')' }) {
                break
            }
            result = result.dropLast(1)
        }
        return result
    }

    fun clean(rawUrl: String): CleanedUrl {
        val url = ensureScheme(stripTrailingPunctuation(rawUrl.trim()))
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return CleanedUrl(rawUrl, rawUrl, emptyList(), "", false)

        val host = (uri.host ?: "").lowercase().removePrefix("www.")
        val rule = TrackingRules.PLATFORM_RULES.firstOrNull { platformRule ->
            platformRule.domains.any { it == host || host.endsWith(".$it") }
        }

        val removed = mutableListOf<String>()
        val keptParams = mutableListOf<Pair<String, String>>()

        for (param in parseQuery(uri.rawQuery)) {
            val key = param.first
            val isGenericTracker = key in TrackingRules.GENERIC_EXACT ||
                TrackingRules.GENERIC_PREFIX.any { key.startsWith(it) }
            val isPlatformTracker = rule?.params?.contains(key) == true ||
                (rule?.params?.any { it.endsWith("_") && key.startsWith(it) } == true)

            if (isGenericTracker || isPlatformTracker) {
                removed.add(key)
            } else {
                keptParams.add(param)
            }
        }

        val newQuery = keptParams.joinToString("&") { (k, v) ->
            if (v.isEmpty()) k else "$k=$v"
        }
        val dropFragment = rule?.dropFragment == true
        val fragment = if (dropFragment) null else uri.rawFragment

        val cleaned = buildString {
            append(uri.scheme).append("://").append(uri.rawAuthority ?: "")
            append(uri.rawPath ?: "")
            if (newQuery.isNotEmpty()) append('?').append(newQuery)
            if (!fragment.isNullOrEmpty()) append('#').append(fragment)
        }

        return CleanedUrl(
            original = rawUrl,
            cleaned = cleaned,
            removedParams = removed,
            domain = host,
            isShortener = TrackingRules.SHORTENER_DOMAINS.any { host == it || host.endsWith(".$it") }
        )
    }

    private fun ensureScheme(url: String): String {
        return if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            url
        } else {
            "https://$url"
        }
    }

    private fun parseQuery(rawQuery: String?): List<Pair<String, String>> {
        if (rawQuery.isNullOrEmpty()) return emptyList()
        return rawQuery.split("&").mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val idx = pair.indexOf('=')
            val rawKey = if (idx == -1) pair else pair.substring(0, idx)
            val rawValue = if (idx == -1) "" else pair.substring(idx + 1)
            val key = runCatching { URLDecoder.decode(rawKey, "UTF-8") }.getOrDefault(rawKey)
            key to rawValue
        }
    }

    /**
     * Plain-language "what did this actually protect you from" sentence,
     * grouped by tracker category — same wording as the bot's
     * describe_removed_trackers(). Returns null if there's nothing to say.
     */
    fun describeRemovedTrackers(removedParams: List<String>, wasShortenerResolved: Boolean): String? {
        val categoriesFound = LinkedHashSet<TrackingRules.TrackerCategory>()
        for (param in removedParams) {
            categoriesFound.add(TrackingRules.categorize(param))
        }

        val sentence = StringBuilder()
        if (categoriesFound.isNotEmpty()) {
            val parts = categoriesFound.map { TrackingRules.CATEGORY_DESCRIPTIONS.getValue(it) }
            val joined = if (parts.size == 1) {
                parts[0]
            } else {
                parts.dropLast(1).joinToString(", ") + " and " + parts.last()
            }
            sentence.append("The original link carried ").append(joined).append(". ")
        }

        if (wasShortenerResolved) {
            sentence.append("It was also a shortened link hiding its real destination until it was resolved.")
        }

        val result = sentence.toString().trim()
        return result.ifEmpty { null }
    }
}
