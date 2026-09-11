package com.blockveil.tracker.remover.cleaner

/**
 * All the tracking-parameter knowledge lives here, ported from the
 * Telegram-bot version of this project (linkcleaner/link_cleaner.py).
 * Kept as plain data so LinkCleaner.kt stays easy to read.
 */
object TrackingRules {

    /** Query params stripped from every URL regardless of domain. */
    val GENERIC_EXACT = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "twclid",
        "igshid", "mc_cid", "mc_eid", "ref", "ref_src", "ref_url",
        "_hsenc", "_hsmi", "spm", "si", "scm"
    )

    /** Prefixes matched with startsWith, e.g. every utm_* variant. */
    val GENERIC_PREFIX = listOf("utm_", "vero_", "oly_", "wt_", "ito_")

    data class PlatformRule(
        val domains: Set<String>,
        val params: Set<String> = emptySet(),
        val dropFragment: Boolean = false
    )

    val PLATFORM_RULES = listOf(
        PlatformRule(
            domains = setOf("youtube.com", "youtu.be", "m.youtube.com"),
            params = setOf("feature", "app", "si")
        ),
        PlatformRule(
            domains = setOf("instagram.com", "www.instagram.com"),
            params = setOf("igshid", "img_index"),
            dropFragment = true
        ),
        PlatformRule(
            domains = setOf("facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch"),
            params = setOf("fbclid", "mibextid", "hrc", "__tn__", "__cft__"),
            dropFragment = true
        ),
        PlatformRule(
            domains = setOf("twitter.com", "x.com"),
            params = setOf("s", "t"),
            dropFragment = true
        ),
        PlatformRule(
            domains = setOf("amazon.com", "amazon.in", "amzn.to", "amzn.in", "www.amazon.com"),
            params = setOf("tag", "linkCode", "ascsubtag", "ref_", "th", "psc"),
            dropFragment = true
        ),
        PlatformRule(
            domains = setOf("tiktok.com", "www.tiktok.com", "vm.tiktok.com"),
            params = setOf("is_from_webapp", "sender_device", "web_id")
        ),
        PlatformRule(
            domains = setOf("linkedin.com", "www.linkedin.com"),
            params = setOf("trk", "trkInfo", "originalSubdomain")
        )
    )

    /** Domains known to be URL shorteners; these get their redirect followed. */
    val SHORTENER_DOMAINS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
        "buff.ly", "rebrand.ly", "cutt.ly", "shorturl.at", "rb.gy",
        "amzn.to", "fb.watch", "vm.tiktok.com", "youtu.be"
    )

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

    /** Same grouping logic as the bot's `_categorize_tracker`. */
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
