package com.blockveil.tracker.remover.safety

import java.net.URI

/**
 * Scores a URL 0-100 from its own shape alone (protocol, host type,
 * brand-lookalike/phishing keyword patterns) — every signal here is
 * TLD-agnostic and needs no network call. Ported from the bot's
 * _score_heuristics(). Starts at 100 and subtracts points per risk signal.
 */
object HeuristicScorer {

    // A suspicious TLD alone is a *weak* signal — plenty of legitimate sites
    // use these, and plenty of phishing happens on completely ordinary .com
    // domains. Kept as a small nudge, not a verdict on its own.
    private val SUSPICIOUS_TLDS = setOf(
        "zip", "mov", "top", "xyz", "work", "click", "link", "gq", "tk", "ml",
        "cf", "ga", "review", "country", "kim", "science", "party", "cricket",
        "date", "faith", "loan", "men", "racing", "download", "stream", "bid",
        "win", "accountant", "gdn", "rest", "surf", "cyou"
    )

    private val PHISHING_KEYWORDS = setOf(
        "login", "verify", "secure", "account", "update", "confirm", "signin",
        "password", "wallet", "billing", "suspended", "unlock", "recover",
        "authenticate", "validation", "security-check", "expire"
    )

    private val BRAND_KEYWORDS = setOf(
        "paypal", "google", "facebook", "microsoft", "apple", "amazon",
        "netflix", "instagram", "whatsapp", "chase", "wellsfargo",
        "binance", "coinbase", "dropbox", "outlook"
    )

    private const val MAX_REASONABLE_URL_LENGTH = 150
    private const val MAX_REASONABLE_HYPHENS = 4
    private const val MAX_REASONABLE_SUBDOMAIN_DEPTH = 3

    data class HeuristicResult(val score: Int, val reasons: List<String>)

    fun score(url: String): HeuristicResult {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return HeuristicResult(40, listOf("Could not parse the URL"))

        val host = (uri.host ?: "").lowercase()
        val path = uri.path.orEmpty()
        var score = 100
        val reasons = mutableListOf<String>()

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            score -= 15
            reasons.add("Not using HTTPS")
        }

        if (isIpHost(host)) {
            score -= 25
            reasons.add("Uses a raw IP address instead of a domain name")
        }

        if (uri.rawAuthority?.contains("@") == true) {
            score -= 25
            reasons.add("Contains an '@' in the address (a common redirect trick)")
        }

        if (looksLikePunycode(host)) {
            score -= 40
            reasons.add("Domain uses punycode (possible lookalike/homograph domain)")
        }

        val tld = host.substringAfterLast('.', missingDelimiterValue = "")
        if (tld in SUSPICIOUS_TLDS) {
            score -= 10
            reasons.add("Uses a TLD sometimes abused for spam/phishing (.$tld)")
        }

        val subdomainDepth = maxOf(host.count { it == '.' } - 1, 0)
        if (subdomainDepth > MAX_REASONABLE_SUBDOMAIN_DEPTH) {
            score -= 10
            reasons.add("Unusually many subdomain levels")
        }

        if (host.count { it == '-' } > MAX_REASONABLE_HYPHENS) {
            score -= 10
            reasons.add("Unusually many hyphens in the domain")
        }

        if (url.length > MAX_REASONABLE_URL_LENGTH) {
            score -= 10
            reasons.add("Unusually long URL")
        }

        val haystack = (host + path).lowercase()
        if (PHISHING_KEYWORDS.any { haystack.contains(it) }) {
            score -= 15
            reasons.add("Contains phishing-style keywords (login/verify/secure/etc.)")
        }

        if (brandImpersonationSignal(host, path)) {
            score -= 25
            reasons.add("Mentions a known brand but isn't that brand's real domain")
        }

        return HeuristicResult(score.coerceIn(0, 100), reasons)
    }

    private fun isIpHost(host: String): Boolean {
        val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        val ipv6 = host.contains(':') // URI host for a bracketed IPv6 literal keeps the brackets; a colon is enough of a signal
        return ipv4.matches(host) || ipv6
    }

    private fun looksLikePunycode(host: String): Boolean =
        host.split(".").any { it.startsWith("xn--") }

    /**
     * True if a well-known brand name shows up in the domain/path, but the
     * domain itself isn't that brand's real *.com — classic phishing pattern
     * (e.g. "paypal-secure-login.top" instead of "paypal.com"). Works on any
     * TLD, including .com lookalikes like "paypal-secure.com".
     */
    private fun brandImpersonationSignal(host: String, path: String): Boolean {
        val haystack = "$host $path".lowercase()
        return BRAND_KEYWORDS.any { brand ->
            haystack.contains(brand) && host != "$brand.com" && !host.endsWith(".$brand.com")
        }
    }
}
