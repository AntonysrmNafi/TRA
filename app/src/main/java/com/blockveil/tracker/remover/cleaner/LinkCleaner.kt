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

    enum class ResolutionKind { NONE, SHORTENER, AMP }

    data class CleanedUrl(
        val original: String,
        val cleaned: String,
        val removedParams: List<String>,
        val domain: String
    )

    /**
     * Mirrors the bot's process_url resolution decision:
     *  - a Google AMP viewer URL embeds the real destination in its own
     *    path, so it can be reconstructed with no network call at all
     *  - a confirmed shortener (curated domain list, or a Facebook
     *    /share/... wrapper path), or something that just looks like an
     *    unrecognized shortlink, needs a real fetch to resolve
     *  - a Google AMP Cache link or bare ".amp.html" page also needs a
     *    real fetch — the real URL is only found via the page's own
     *    canonical link, not embedded in the URL itself
     */
    data class ResolutionDecision(
        val kind: ResolutionKind,
        /** True only for a *confirmed* shortener/wrapper, not just the generic short-path guess. */
        val confirmedShortener: Boolean,
        /** Set only when the real URL could be reconstructed with no network call (AMP viewer). */
        val directResolvedUrl: String?
    )

    fun decideResolution(url: String): ResolutionDecision {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return ResolutionDecision(ResolutionKind.NONE, confirmedShortener = false, directResolvedUrl = null)

        val host = (uri.host ?: "").lowercase().removePrefix("www.")
        val path = uri.path.orEmpty()
        val hasQuery = !uri.rawQuery.isNullOrEmpty()

        val confirmedShortener = TrackingRules.isKnownShortener(host, path)
        val ampDirect = TrackingRules.unwrapGoogleAmpViewer(host, path, uri.rawQuery)

        return when {
            ampDirect != null -> ResolutionDecision(ResolutionKind.AMP, confirmedShortener, ampDirect)
            confirmedShortener || TrackingRules.looksLikeUnknownShortlink(host, path, hasQuery) ->
                ResolutionDecision(ResolutionKind.SHORTENER, confirmedShortener, directResolvedUrl = null)
            TrackingRules.isAmpUrl(host, path) ->
                ResolutionDecision(ResolutionKind.AMP, confirmedShortener, directResolvedUrl = null)
            else -> ResolutionDecision(ResolutionKind.NONE, confirmedShortener, directResolvedUrl = null)
        }
    }

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
            ?: return CleanedUrl(rawUrl, rawUrl, emptyList(), "")

        val host = (uri.host ?: "").lowercase().removePrefix("www.")
        val rule = TrackingRules.PLATFORM_RULES.firstOrNull { platformRule ->
            platformRule.domains.any { it == host || host.endsWith(".$it") }
        }
        val platformParams = rule?.params.orEmpty().map { it.lowercase() }.toSet()

        val removed = mutableListOf<String>()
        val keptParams = mutableListOf<Pair<String, String>>()

        for (param in parseQuery(uri.rawQuery)) {
            val key = param.first
            val keyLower = key.lowercase()
            val isGenericTracker = keyLower in TrackingRules.GENERIC_EXACT ||
                TrackingRules.GENERIC_PREFIX.any { keyLower.startsWith(it) }
            val isPlatformTracker = keyLower in platformParams

            if (isGenericTracker || isPlatformTracker) {
                removed.add(key) // keep original casing in what's shown to the user
            } else {
                keptParams.add(param)
            }
        }

        val newQuery = keptParams.joinToString("&") { (k, v) ->
            if (v.isEmpty()) k else "$k=$v"
        }
        val dropFragment = rule?.dropFragment == true
        val hadFragment = !uri.rawFragment.isNullOrEmpty()
        if (dropFragment && hadFragment) {
            removed.add("fragment")
        }
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
            domain = host
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
    fun describeRemovedTrackers(
        removedParams: List<String>,
        wasRedirected: Boolean,
        resolutionKind: ResolutionKind
    ): String? {
        val categoriesFound = LinkedHashSet<TrackingRules.TrackerCategory>()
        for (param in removedParams) {
            if (param == "fragment") continue
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

        if (wasRedirected) {
            val kindLabel = if (resolutionKind == ResolutionKind.AMP) "an AMP proxy link" else "a shortened link"
            sentence.append("It was also ").append(kindLabel)
                .append(" hiding its real destination until it was resolved.")
        }

        val result = sentence.toString().trim()
        return result.ifEmpty { null }
    }
}
