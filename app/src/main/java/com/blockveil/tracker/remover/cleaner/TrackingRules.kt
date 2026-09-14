package com.blockveil.tracker.remover.cleaner

/**
 * All the tracking-parameter knowledge lives here, ported line-for-line
 * from the Telegram-bot version of this project (linkcleaner/link_cleaner.py)
 * so the two stay in sync. Kept as plain data so LinkCleaner.kt stays easy
 * to read.
 */
object TrackingRules {

    /** Tracking params stripped regardless of domain. Matched case-insensitively. */
    val GENERIC_EXACT = setOf(
        "fbclid", "mibextid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid",
        "gad_source", "msclkid", "ttclid", "twclid", "yclid", "ysclid",
        "igsh", "igshid", "si", "ncid", "cmpid", "icid", "ito",
        "mc_cid", "mc_eid", "mkt_tok", "vero_id", "_hsenc", "_hsmi",
        "hsctatracking", "elqtrackid", "oly_enc_id", "oly_anon_id",
        "ref", "ref_src", "ref_url", "spm", "scm",
        // Ad-platform click IDs and cross-domain measurement params not
        // already covered above.
        "srsltid", // Google Shopping/Search result click ID
        "li_fat_id", // LinkedIn ads click ID
        "epik", // Pinterest ads click ID
        "rdt_cid", // Reddit ads click ID
        "ocid", // Microsoft/Outlook campaign tracking ID
        "_ga", "_gl" // Google Analytics cross-domain linker params
    )

    /** Prefixes matched case-insensitively with startsWith, e.g. every utm_* variant. */
    val GENERIC_PREFIX = listOf("utm_", "pf_rd_", "pd_rd_", "__cft__", "__tn__")

    data class PlatformRule(
        val domains: Set<String>,
        val params: Set<String> = emptySet(),
        val dropFragment: Boolean = false
    )

    /** Matched against the request host, including subdomains (e.g. m.youtube.com matches "youtube.com"). */
    val PLATFORM_RULES = listOf(
        PlatformRule(
            domains = setOf("facebook.com", "fb.com", "fb.watch", "fb.me", "messenger.com", "m.me"),
            params = setOf(
                "fbclid", "mibextid", "__tn__", "refsrc", "source", "extid",
                "paipv", "eav", "notif_id", "notif_t", "ref_component", "actorid", "hrc",
                "rdid", "share_url", "sfnsn",
                "fb_action_ids", "fb_action_types", "fb_source", "fb_ref"
            ),
            dropFragment = true
        ),
        PlatformRule(
            domains = setOf("youtube.com", "youtu.be", "music.youtube.com"),
            params = setOf("si", "feature", "ab_channel", "pp", "kw")
        ),
        PlatformRule(
            domains = setOf("twitter.com", "x.com", "t.co"),
            params = setOf("s", "t", "src")
        ),
        PlatformRule(
            domains = setOf("instagram.com", "instagr.am"),
            params = setOf("igsh", "igshid")
        ),
        PlatformRule(
            domains = setOf("tiktok.com", "vm.tiktok.com", "vt.tiktok.com"),
            params = setOf(
                "share_app_id", "checksum", "sender_device", "sender_web_id",
                "tt_from", "is_from_webapp", "is_copy_url", "u_code",
                "share_item_id", "source", "enter_from", "_t", "_r", "ttclid"
            ),
            dropFragment = true
        ),
        PlatformRule(
            domains = setOf("linkedin.com", "lnkd.in"),
            params = setOf(
                "trk", "trkcampaign", "trkemail", "rcm", "midtoken",
                "midsig", "originalsubdomain", "lipi", "otptoken", "eid",
                "trackingid"
            )
        ),
        PlatformRule(
            // "sccid" (lowercased) is Snapchat's real ad click ID.
            domains = setOf("snapchat.com", "story.snapchat.com"),
            params = setOf("share_id", "sccid", "attributionid")
        ),
        PlatformRule(
            domains = setOf("reddit.com", "redd.it"),
            params = setOf("share_id")
        ),
        PlatformRule(
            domains = setOf("pinterest.com", "pin.it"),
            params = setOf("sender", "sender_id", "invite_code", "share_id")
        ),
        PlatformRule(
            domains = setOf("amazon.com", "amazon.in", "amazon.co.uk", "amazon.de"),
            params = setOf(
                "tag", "ref_", "linkcode", "camp", "creative",
                "creativeasin", "psc", "spla", "keywords_id"
            )
        ),
        PlatformRule(
            domains = setOf("google.com"),
            params = setOf("ved", "uact", "sxsrf", "ei", "sa", "gs_lcrp", "g_ep", "g_st")
        ),
        PlatformRule(
            domains = setOf("spotify.com", "open.spotify.com", "spotify.link"),
            params = setOf("si", "nd")
        ),
        PlatformRule(
            domains = setOf("threads.net", "threads.com"),
            params = setOf("igsh", "igshid", "xmt")
        ),
        // No known Telegram-specific tracker params. Deliberately does NOT
        // strip "start"/"startapp" (deep-link payloads a bot/mini-app needs
        // to function) or "text" (t.me/share/url prefills) — those aren't
        // trackers, removing them would break the link.
        PlatformRule(domains = setOf("t.me", "telegram.me", "telegram.dog")),
        // No known Discord-specific tracker params on invite links.
        PlatformRule(domains = setOf("discord.gg", "discord.com", "discordapp.com"))
    )

    /** Curated shortener domains that always get their redirect followed. */
    val SHORTENER_DOMAINS = setOf(
        "bit.ly", "t.co", "lnkd.in", "vm.tiktok.com", "vt.tiktok.com",
        "fb.watch", "fb.me", "goo.gl", "amzn.to", "pin.it", "redd.it",
        "spotify.link",
        "tinyurl.com", "is.gd", "ow.ly", "buff.ly", "shorturl.at", "rebrand.ly",
        "cutt.ly", "soo.gd", "tiny.cc", "rb.gy", "s.id", "bl.ink", "shrtco.de",
        "v.gd", "qr.ae", "tr.im", "adf.ly", "tny.im", "x.co", "cli.gs",
        "shorte.st", "po.st", "mcaf.ee", "ln.run", "git.io", "dub.sh", "t.ly",
        "snip.ly", "0rz.tw", "urlz.fr", "hyperurl.co", "chilp.it", "kutt.it",
        "gg.gg", "clck.ru", "u.to", "waa.ai", "zpr.io", "urlr.me", "shorturl.com",
        "shorturl.gg", "tiny.one", "smallurl.co", "rotf.lu", "urlz.de",
        "youtu.be"
    )

    /**
     * Facebook's /share/v/... and /share/r/... links (like the one from the
     * Android share sheet) are wrapper links even though they live on
     * facebook.com itself — a direct facebook.com/reel/... or
     * facebook.com/watch?v=... link does not need resolving.
     */
    private val FACEBOOK_HOST_SUFFIXES = setOf(
        "facebook.com", "fb.watch", "fb.com", "fb.me", "messenger.com", "m.me"
    )

    /** Domains where the unrecognized short-path heuristic below should never fire. */
    private val RECOGNIZED_PLATFORM_DOMAINS: Set<String> = PLATFORM_RULES.flatMap { it.domains }.toSet()

    // A single short random-looking path segment with no query string, e.g.
    // "/bh0P2" — the typical shape of a URL-shortener slug. New shorteners
    // launch constantly, so this is the backstop for ones not in the
    // curated list above.
    private val GENERIC_SHORT_PATH_RE = Regex("^/(?=[A-Za-z0-9]*\\d)(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]{4,12}/?$")

    /** True for a domain in the curated list, or a Facebook /share/... wrapper path. */
    fun isKnownShortener(host: String, path: String): Boolean {
        if (SHORTENER_DOMAINS.any { host == it || host.endsWith(".$it") }) return true
        if (FACEBOOK_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) {
            return path.startsWith("/share/")
        }
        return false
    }

    /**
     * True if `host` isn't one already recognized, and `path` is a single
     * short random-looking segment with no query string — worth trying to
     * resolve even though it's not on the curated shortener list.
     */
    fun looksLikeUnknownShortlink(host: String, path: String, hasQuery: Boolean): Boolean {
        if (host in RECOGNIZED_PLATFORM_DOMAINS) return false
        if (RECOGNIZED_PLATFORM_DOMAINS.any { host.endsWith(".$it") }) return false
        if (hasQuery) return false
        return GENERIC_SHORT_PATH_RE.matches(path)
    }

    // ==== AMP link detection/unwrapping ====================================
    // AMP ("Accelerated Mobile Pages") links point at a cache/proxy copy of a
    // page instead of the page itself, e.g. a Google Search result opening
    // "google.com/amp/s/example.com/article" or a Google AMP Cache URL like
    // "example-com.cdn.ampproject.org/c/s/example.com/article" instead of
    // just "example.com/article". These aren't shorteners, but the user
    // almost certainly wants the real underlying article link.

    private const val AMP_CACHE_DOMAIN_SUFFIX = ".cdn.ampproject.org"

    // Matches Google's AMP viewer path shape: /amp/s/example.com/page
    // (https) or /amp/example.com/page (http, no "s/").
    private val GOOGLE_AMP_VIEWER_PATH_RE = Regex("^/amp/(s/)?(.+)$")

    /** True if `host`/`path` is an AMP proxy/cache link or an AMP page variant, rather than a direct link. */
    fun isAmpUrl(host: String, path: String): Boolean {
        if (host == "cdn.ampproject.org" || host.endsWith(AMP_CACHE_DOMAIN_SUFFIX)) return true
        if (host == "google.com" && GOOGLE_AMP_VIEWER_PATH_RE.matches(path)) return true
        return path.lowercase().endsWith(".amp.html")
    }

    /**
     * Directly reconstructs the underlying URL from a Google AMP viewer link
     * (e.g. "google.com/amp/s/example.com/article" -> "https://example.com/article"),
     * with no network request needed. Returns null if `url` isn't in that
     * specific shape — a Google AMP Cache domain or a bare ".amp.html" page
     * still needs a real fetch to find the canonical URL.
     */
    fun unwrapGoogleAmpViewer(host: String, path: String, query: String?): String? {
        if (host != "google.com") return null
        val match = GOOGLE_AMP_VIEWER_PATH_RE.find(path) ?: return null
        val hasHttpsMarker = match.groupValues[1] == "s/"
        val scheme = if (hasHttpsMarker) "https" else "http"
        val rest = runCatching { java.net.URLDecoder.decode(match.groupValues[2], "UTF-8") }
            .getOrDefault(match.groupValues[2])
        val withQuery = if (!query.isNullOrEmpty()) "$rest?$query" else rest
        return "$scheme://$withQuery"
    }

    // ==== Tracker categories + plain-language descriptions =================
    // Same grouping as the Telegram bot (link_cleaner.py), so the app gives
    // the same "what did this actually protect you from" explanation.

    enum class TrackerCategory { AD_CLICK, CAMPAIGN, CROSS_SITE, SHARE_TRACE, OTHER }

    val CATEGORY_DESCRIPTIONS = mapOf(
        TrackerCategory.AD_CLICK to "an ad-click ID that can tie the click back to your identity and the specific ad or post it came from",
        TrackerCategory.CAMPAIGN to "a marketing-campaign tag used only to measure engagement, not needed to view the page",
        TrackerCategory.CROSS_SITE to "an analytics identifier that can link your activity across different websites",
        TrackerCategory.SHARE_TRACE to "a share-tracing code that can reveal which app or contact the link was shared through",
        TrackerCategory.OTHER to "platform-specific tracking data attached when the link was shared"
    )

    private val AD_CLICK_TRACKERS = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid",
        "ttclid", "twclid", "yclid", "ysclid", "li_fat_id", "epik", "rdt_cid",
        "srsltid", "gad_source", "ocid", "sccid"
    )
    private val CAMPAIGN_TRACKERS = setOf(
        "mc_cid", "mc_eid", "mkt_tok", "vero_id", "_hsenc", "_hsmi",
        "hsctatracking", "elqtrackid", "oly_enc_id", "oly_anon_id",
        "ncid", "cmpid", "icid", "ito", "spm", "scm"
    )
    private val CROSS_SITE_TRACKERS = setOf("_ga", "_gl")
    private val SHARE_TRACE_TRACKERS = setOf(
        "igsh", "igshid", "si", "sfnsn", "mibextid", "share_url", "xmt",
        "__tn__", "refsrc", "source", "extid", "fb_source", "fb_ref"
    )

    /** Same grouping logic as the bot's `_categorize_tracker`. "fragment" is handled by the caller, not here. */
    fun categorize(paramName: String): TrackerCategory {
        val name = paramName.lowercase()
        return when {
            name.startsWith("utm_") -> TrackerCategory.CAMPAIGN
            name in AD_CLICK_TRACKERS -> TrackerCategory.AD_CLICK
            name in CAMPAIGN_TRACKERS -> TrackerCategory.CAMPAIGN
            name in CROSS_SITE_TRACKERS -> TrackerCategory.CROSS_SITE
            name in SHARE_TRACE_TRACKERS -> TrackerCategory.SHARE_TRACE
            else -> TrackerCategory.OTHER
        }
    }
}
