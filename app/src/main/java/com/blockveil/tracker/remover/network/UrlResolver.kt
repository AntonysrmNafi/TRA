package com.blockveil.tracker.remover.network

import okhttp3.Request
import java.net.InetAddress
import java.net.URI

/**
 * Follows redirects for shortener links (bit.ly, t.co, amzn.to, ...) so the
 * user sees where a link actually goes before opening it.
 *
 * Runs on a background thread; call from a coroutine, never the main thread.
 */
object UrlResolver {

    private const val MAX_HOPS = 6

    sealed class Result {
        data class Resolved(val finalUrl: String, val hops: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun resolveFinalUrl(startUrl: String): Result {
        var current = startUrl
        repeat(MAX_HOPS) { hop ->
            if (!isSafeToFetch(current)) {
                return Result.Failed("Blocked an unsafe redirect target")
            }
            val request = Request.Builder()
                .url(current)
                .head() // HEAD is enough, we only need the Location header
                .build()

            val response = runCatching {
                HttpClientProvider.client.newCall(request).execute()
            }.getOrElse { return Result.Failed(it.message ?: "Network error") }

            response.use {
                if (!it.isRedirect) {
                    return Result.Resolved(current, hop)
                }
                val location = it.header("Location")
                    ?: return Result.Resolved(current, hop)
                current = resolveRelative(current, location)
            }
        }
        return Result.Resolved(current, MAX_HOPS)
    }

    private fun resolveRelative(base: String, location: String): String {
        return runCatching { URI(base).resolve(location).toString() }.getOrDefault(location)
    }

    /**
     * Refuses to fetch anything other than plain http/https pointed at a
     * public address. Mirrors the SSRF guard from the bot version: on a
     * single-user device the blast radius is smaller, but a malicious
     * shortener redirecting into the phone's own local network (router
     * admin pages, other apps' local servers) is still worth blocking.
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
