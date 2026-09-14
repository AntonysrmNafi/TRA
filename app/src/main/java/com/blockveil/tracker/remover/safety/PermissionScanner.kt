package com.blockveil.tracker.remover.safety

import com.blockveil.tracker.remover.network.HttpClientProvider
import okhttp3.Request
import java.net.InetAddress
import java.net.URI

/**
 * A common scam/phishing pattern is a page that immediately (on load, or on
 * the very first tap) asks for camera, microphone, or location access —
 * fake "verify you're human", fake video-call, or fake "nearby deals" pages
 * being the usual bait. This is a TLD-agnostic, domain-agnostic signal: it
 * catches this on a plain .com just as well as on a sketchy .top.
 *
 * This is a static scan of the page's own HTML/JS source for the browser
 * APIs that trigger a permission prompt, not a real browser — it can't see
 * permissions requested by code fetched from a separate JS bundle it
 * doesn't also fetch, or gated behind a user interaction the scan can't
 * perform. Best-effort signal, not a guarantee. Ported from the bot's
 * _scan_permission_requests().
 */
object PermissionScanner {

    private const val BYTE_LIMIT = 300_000L // generous cap on how much of the page we read
    private val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml"
    )

    // Order defines the order permission types are reported in.
    private val JS_PATTERNS = linkedMapOf(
        "camera/microphone access" to Regex("""getUserMedia\s*\(""", RegexOption.IGNORE_CASE),
        "screen-sharing access" to Regex("""getDisplayMedia\s*\(""", RegexOption.IGNORE_CASE),
        "location access" to Regex(
            """geolocation\s*\.\s*(getCurrentPosition|watchPosition)\s*\(""", RegexOption.IGNORE_CASE
        ),
        "notification permission" to Regex("""Notification\s*\.\s*requestPermission\s*\(""", RegexOption.IGNORE_CASE),
        "camera/microphone/location permission" to Regex(
            """permissions\s*\.\s*query\s*\(\s*\{\s*name\s*:\s*['"](camera|microphone|geolocation)['"]""",
            RegexOption.IGNORE_CASE
        )
    )

    /**
     * Returns the distinct permission types (in pattern-definition order)
     * the page's HTML/JS source asks the browser for. Empty list if the
     * page couldn't be fetched, isn't HTML, or the fetch target isn't safe
     * to reach — never throws.
     */
    fun scan(url: String): List<String> {
        if (!isSafeToFetch(url)) return emptyList()

        val text = runCatching {
            val request = Request.Builder().url(url).apply {
                HEADERS.forEach { (k, v) -> header(k, v) }
            }.get().build()

            HttpClientProvider.client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("html", ignoreCase = true)) return@use null
                val source = response.body?.source() ?: return@use null
                source.request(BYTE_LIMIT)
                val size = minOf(source.buffer.size, BYTE_LIMIT)
                source.buffer.readByteArray(size).toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return emptyList()

        return JS_PATTERNS.filterValues { it.containsMatchIn(text) }.keys.toList()
    }

    /** Same SSRF guard shape as UrlResolver — never fetch anything but a public http/https address. */
    private fun isSafeToFetch(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false

        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return false
        return addresses.all { addr ->
            !addr.isLoopbackAddress && !addr.isLinkLocalAddress &&
                !addr.isSiteLocalAddress && !addr.isMulticastAddress &&
                addr.hostAddress != "169.254.169.254"
        }
    }
}
