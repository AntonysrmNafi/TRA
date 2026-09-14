package com.blockveil.tracker.remover.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.net.InetAddress
import java.net.URI

/**
 * Follows redirects for shortener/wrapper links (bit.ly, t.co, amzn.to,
 * Facebook's /share/... links, ...) so the user sees where a link
 * actually goes before opening it.
 *
 * Some platforms — Facebook's /share/v/... and /share/r/... links are the
 * clearest example — don't send a normal HTTP redirect to generic bot
 * traffic, but do serve one to known link-preview crawlers, or embed the
 * destination in the HTML instead of redirecting at all. This mirrors
 * what the Telegram-bot version of this project does for those:
 *   1. only http/https schemes are followed, every hop SSRF-validated
 *   2. redirects are followed manually (one hop at a time), never via
 *      OkHttp's own follow-redirects, so each hop gets re-validated
 *   3. platform-appropriate crawler User-Agents are used where that's the
 *      standard, documented way to get the real destination back
 *      (this is exactly how link previews work on Messenger/WhatsApp/
 *      Slack/Twitter — impersonating that same crawler UA is standard
 *      practice, not something adversarial)
 *   4. a small anonymous cookie warm-up for Facebook hosts, since it
 *      increasingly login-walls content for requests with zero prior
 *      cookies at all, even when the content itself is public
 *   5. if there's no HTTP redirect, a capped read of the HTML looks for
 *      the destination in an og:url tag, a canonical link, a meta-refresh,
 *      or an embedded JSON-escaped URL
 *   6. a resolved URL that looks like a login/authwall bounce is discarded
 *      in favor of the original link, so a working short link never gets
 *      replaced by something worse
 *
 * Runs on a background thread; call from a coroutine, never the main thread.
 */
object UrlResolver {

    private const val MAX_HOPS = 6
    private const val CANONICAL_SCAN_LIMIT = 300_000L // bytes; only the <head> is needed, generous cap

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    private val DEFAULT_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    private val FACEBOOK_HOST_SUFFIXES = listOf(
        "facebook.com", "fb.watch", "fb.com", "fb.me", "messenger.com", "m.me"
    )
    private val FACEBOOK_HEADERS = mapOf(
        "User-Agent" to "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
        "Accept" to "*/*"
    )
    private const val FACEBOOK_WARMUP_URL = "https://www.facebook.com/"

    private val TWITTER_HOST_SUFFIXES = listOf("twitter.com", "x.com", "t.co")
    private val TWITTER_HEADERS = mapOf("User-Agent" to "Twitterbot/1.0", "Accept" to "*/*")

    private val OG_URL_RE = Regex(
        """<meta\b(?=[^>]*\bproperty\s*=\s*["']og:url["'])(?=[^>]*\bcontent\s*=\s*["']([^"']+)["'])[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val CANONICAL_RE = Regex(
        """<link\b(?=[^>]*\brel\s*=\s*["']canonical["'])(?=[^>]*\bhref\s*=\s*["']([^"']+)["'])[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val META_REFRESH_RE = Regex(
        """<meta\b(?=[^>]*\bhttp-equiv\s*=\s*["']refresh["'])(?=[^>]*\bcontent\s*=\s*["'][^"']*url=([^"'&]+))[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val JSON_ESCAPED_URL_RE = Regex(""""url"\s*:\s*"(https:\\/\\/[^"]+)"""", RegexOption.IGNORE_CASE)

    // Landing on one of these after following a redirect/canonical chain
    // almost always means "you're not logged in" rather than "here's the
    // content" (e.g. LinkedIn bouncing an unauthenticated request to its
    // homepage or an authwall page). In that case the resolved URL is
    // strictly worse than what the user gave us, so the original is kept.
    private val AUTHWALL_PATH_MARKERS = listOf("authwall", "checkpoint", "uas/login", "login", "signin", "consent")
    private val BOUNCE_PRONE_DOMAINS = listOf("linkedin.com", "tiktok.com")

    sealed class Result {
        data class Resolved(val finalUrl: String, val hops: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun resolveFinalUrl(startUrl: String): Result {
        val client = buildCookieAwareClient()
        warmUpFacebookCookies(client, startUrl)

        var current = startUrl
        for (hop in 0..MAX_HOPS) {
            if (!isSafeToFetch(current)) {
                return Result.Failed("Blocked an unsafe redirect target")
            }

            val requestBuilder = Request.Builder().url(current).get()
            (DEFAULT_HEADERS + headersFor(current)).forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = runCatching {
                client.newCall(requestBuilder.build()).execute()
            }.getOrElse { return Result.Failed(it.message ?: "Network error") }

            response.use { resp ->
                val location = resp.header("Location")
                if (resp.code in REDIRECT_CODES && location != null) {
                    current = resolveRelative(current, location)
                    return@use
                }

                val contentType = resp.header("Content-Type").orEmpty()
                val canonical = if (contentType.contains("text/html", ignoreCase = true)) {
                    extractCanonicalUrl(resp)
                } else {
                    null
                }

                if (canonical != null) {
                    val candidate = resolveRelative(current, canonical)
                    if (candidate != current && isSafeToFetch(candidate)) {
                        current = candidate
                        return@use
                    }
                }

                val finalUrl = guardAgainstAuthwall(startUrl, resp.request.url.toString())
                return Result.Resolved(finalUrl, hop)
            }
        }
        return Result.Resolved(current, MAX_HOPS)
    }

    private fun buildCookieAwareClient(): okhttp3.OkHttpClient {
        val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
        return HttpClientProvider.client.newBuilder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore.getOrPut(url.host) { mutableListOf() }.addAll(cookies)
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    cookieStore[url.host].orEmpty()
            })
            .build()
    }

    /**
     * Facebook increasingly login-walls video/reel content for requests
     * that show up with zero prior cookies at all, even when the content
     * is public — a real browser's very first visit already carries a
     * baseline anonymous cookie Facebook itself sets. This does the same
     * anonymous warm-up: one throwaway GET to the Facebook homepage so the
     * cookie jar picks up that baseline cookie before the real request.
     * Best-effort — if it fails for any reason, resolution still proceeds.
     */
    private fun warmUpFacebookCookies(client: okhttp3.OkHttpClient, url: String) {
        val host = hostOf(url) ?: return
        if (FACEBOOK_HOST_SUFFIXES.none { host == it || host.endsWith(".$it") }) return
        if (!isSafeToFetch(FACEBOOK_WARMUP_URL)) return

        runCatching {
            val request = Request.Builder().url(FACEBOOK_WARMUP_URL).apply {
                FACEBOOK_HEADERS.forEach { (k, v) -> header(k, v) }
            }.get().build()
            client.newCall(request).execute().close()
        }
    }

    private fun headersFor(url: String): Map<String, String> {
        val host = hostOf(url) ?: return emptyMap()
        if (FACEBOOK_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) return FACEBOOK_HEADERS
        if (TWITTER_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) return TWITTER_HEADERS
        return emptyMap()
    }

    /** Reads up to CANONICAL_SCAN_LIMIT bytes of an HTML response looking for the real destination URL. */
    private fun extractCanonicalUrl(response: Response): String? {
        val text = runCatching {
            val source = response.body?.source() ?: return null
            source.request(CANONICAL_SCAN_LIMIT)
            val size = minOf(source.buffer.size, CANONICAL_SCAN_LIMIT)
            source.buffer.readByteArray(size).toString(Charsets.UTF_8)
        }.getOrNull() ?: return null

        (OG_URL_RE.find(text) ?: CANONICAL_RE.find(text) ?: META_REFRESH_RE.find(text))?.let {
            return it.groupValues[1]
        }
        JSON_ESCAPED_URL_RE.find(text)?.let {
            return it.groupValues[1].replace("\\/", "/")
        }
        return null
    }

    private fun guardAgainstAuthwall(original: String, resolved: String): String {
        if (resolved == original) return resolved
        val originalPath = runCatching { URI(original).path }.getOrNull().orEmpty()
        if (originalPath.isEmpty() || originalPath == "/") return resolved

        val resolvedUri = runCatching { URI(resolved) }.getOrNull() ?: return resolved
        val resolvedPath = (resolvedUri.path ?: "").lowercase()
        val resolvedHost = (resolvedUri.host ?: "").lowercase().removePrefix("www.")

        val isAuthwallKeyword = AUTHWALL_PATH_MARKERS.any { resolvedPath.contains(it) }
        val isBounceProneHome = (resolvedPath.isEmpty() || resolvedPath == "/") &&
            BOUNCE_PRONE_DOMAINS.any { resolvedHost == it || resolvedHost.endsWith(".$it") }

        return if (isAuthwallKeyword || isBounceProneHome) original else resolved
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()

    private fun resolveRelative(base: String, location: String): String {
        return runCatching { URI(base).resolve(location).toString() }.getOrDefault(location)
    }

    /**
     * Refuses to fetch anything other than plain http/https pointed at a
     * public address. Mirrors the SSRF guard from the bot version: on a
     * single-user device the blast radius is smaller, but a malicious
     * redirect into the phone's own local network (router admin pages,
     * other apps' local servers) is still worth blocking.
     */
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
