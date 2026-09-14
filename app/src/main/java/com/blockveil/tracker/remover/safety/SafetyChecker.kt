package com.blockveil.tracker.remover.safety

import com.blockveil.tracker.remover.data.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Combines several independent signals into one 0-100 safety score +
 * verdict, mirroring the bot's check_url_safety() precedence exactly:
 *
 *   1. Safe Browsing confirmed match             -> forced to unsafe
 *   2. VirusTotal, 3+ engines flagging malicious  -> forced to unsafe
 *   3. everything else (permission requests, a young domain, VirusTotal
 *      with 1-2 engines, and URL-shape heuristics) stacks as point
 *      deductions from a 100 baseline
 *
 * If neither Safe Browsing nor VirusTotal returned a usable result (not
 * enabled, or both failed), the score is capped below 100 — there's no
 * real threat-intel behind it, only shape/content signals.
 *
 * Every individual check fails soft: a missing key or a network error
 * just means "skip this check", never "mark as unsafe".
 */
object SafetyChecker {

    private const val VIRUSTOTAL_HIGH_CONFIDENCE_ENGINE_COUNT = 3
    private const val VIRUSTOTAL_LOW_CONFIDENCE_PENALTY = 30

    private const val NEW_DOMAIN_VERY_RECENT_DAYS = 7
    private const val NEW_DOMAIN_RECENT_DAYS = 30
    private const val NEW_DOMAIN_VERY_RECENT_PENALTY = 30
    private const val NEW_DOMAIN_RECENT_PENALTY = 15

    // A heuristic-only result (no real threat-intel confirmed clean/dirty)
    // can never claim full confidence.
    private const val HEURISTIC_ONLY_SCORE_CAP = 95

    // Losing camera/mic/location access to a page you never meant to grant
    // it to is a serious, immediate real-world risk, so this outweighs any
    // single URL-shape signal and can push a link into "unsafe" on its own.
    private const val PERMISSION_REQUEST_PENALTY = 40

    private val MULTI_PART_TLDS = setOf(
        "co.uk", "org.uk", "gov.uk", "ac.uk", "me.uk", "ltd.uk", "net.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.in", "net.in", "org.in", "gen.in", "firm.in",
        "com.bd", "net.bd", "org.bd", "gov.bd", "edu.bd", "ac.bd",
        "co.jp", "ne.jp", "or.jp",
        "co.kr", "or.kr",
        "com.br", "net.br", "org.br",
        "com.cn", "net.cn", "org.cn",
        "com.sg", "com.my", "com.pk", "com.np"
    )

    /** "sub.example.co.uk" -> "example.co.uk", "www.example.com" -> "example.com". */
    private fun registrableDomain(host: String): String {
        val labels = host.lowercase().split(".")
        if (labels.size < 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        if (lastTwo in MULTI_PART_TLDS && labels.size >= 3) {
            return labels.takeLast(3).joinToString(".")
        }
        return lastTwo
    }

    /**
     * @param originalUrl the link before resolving (may be a shortener/AMP
     *   wrapper) — checked alongside the resolved link since a malicious
     *   shortener can be flagged even before its destination is known.
     */
    suspend fun check(originalUrl: String, cleanedUrl: String, domain: String, settings: SettingsRepository): SafetyResult =
        coroutineScope {
            val safeBrowsingDeferred = async {
                if (!settings.safeBrowsingEnabled) return@async null
                SafeBrowsingClient.check(listOf(originalUrl, cleanedUrl), settings.safeBrowsingApiKey)
            }
            val virusTotalDeferred = async {
                if (!settings.virusTotalEnabled) return@async null
                VirusTotalClient.check(cleanedUrl, settings.virusTotalApiKey)
            }
            val domainAgeDeferred = async {
                if (!settings.domainAgeEnabled) return@async null
                DomainAgeClient.lookupAgeDays(registrableDomain(domain))
            }
            // Free, no key needed — bundled under the same "any safety check
            // enabled" umbrella as the toggles above rather than a fourth
            // switch, since both work with zero configuration.
            val permissionsDeferred = async { PermissionScanner.scan(cleanedUrl) }
            val heuristics = HeuristicScorer.score(cleanedUrl)

            val safeBrowsingFlagged = safeBrowsingDeferred.await()
            val virusTotalResult = virusTotalDeferred.await()
            val domainAgeDays = domainAgeDeferred.await()
            val permissionsFound = permissionsDeferred.await()

            val safeBrowsingChecked = safeBrowsingFlagged != null
            val virusTotalChecked = virusTotalResult != null
            val domainAgeChecked = settings.domainAgeEnabled && domainAgeDays != null

            var score = heuristics.score
            val reasons = heuristics.reasons.toMutableList()

            if (domainAgeChecked && domainAgeDays != null) {
                when {
                    domainAgeDays < NEW_DOMAIN_VERY_RECENT_DAYS -> {
                        score = (score - NEW_DOMAIN_VERY_RECENT_PENALTY).coerceAtLeast(0)
                        reasons.add("Domain was registered only $domainAgeDays day(s) ago")
                    }
                    domainAgeDays < NEW_DOMAIN_RECENT_DAYS -> {
                        score = (score - NEW_DOMAIN_RECENT_PENALTY).coerceAtLeast(0)
                        reasons.add("Domain was registered recently ($domainAgeDays days ago)")
                    }
                }
            }

            if (permissionsFound.isNotEmpty()) {
                score = (score - PERMISSION_REQUEST_PENALTY).coerceAtLeast(0)
                reasons.add(0, "\u26A0\uFE0F Page immediately requests: ${permissionsFound.joinToString(", ")}")
            }

            val virusTotalMalicious = virusTotalResult?.malicious ?: 0
            val virusTotalSuspicious = virusTotalResult?.suspicious ?: 0
            val virusTotalHighConfidence = virusTotalChecked && virusTotalMalicious >= VIRUSTOTAL_HIGH_CONFIDENCE_ENGINE_COUNT
            if (virusTotalChecked && !virusTotalHighConfidence && (virusTotalMalicious > 0 || virusTotalSuspicious >= 2)) {
                score = (score - VIRUSTOTAL_LOW_CONFIDENCE_PENALTY).coerceAtLeast(0)
                reasons.add("Flagged as suspicious by ${virusTotalMalicious + virusTotalSuspicious} security engine(s) on VirusTotal")
            }

            if (safeBrowsingFlagged == true) {
                score = score.coerceAtMost(10)
                reasons.add(0, "\uD83D\uDEA8 Flagged by Google Safe Browsing as a known threat")
                return@coroutineScope SafetyResult(
                    score = score, verdict = Verdict.UNSAFE, reasons = reasons,
                    safeBrowsingChecked = true, safeBrowsingFlagged = true,
                    virusTotalChecked = virusTotalChecked, virusTotalFlagged = virusTotalHighConfidence,
                    domainAgeChecked = domainAgeChecked, domainAgeDays = domainAgeDays,
                    permissionsFound = permissionsFound
                )
            }

            if (virusTotalHighConfidence) {
                score = score.coerceAtMost(10)
                reasons.add(0, "\uD83D\uDEA8 Flagged as malicious by $virusTotalMalicious security engines on VirusTotal")
                return@coroutineScope SafetyResult(
                    score = score, verdict = Verdict.UNSAFE, reasons = reasons,
                    safeBrowsingChecked = safeBrowsingChecked, safeBrowsingFlagged = false,
                    virusTotalChecked = true, virusTotalFlagged = true,
                    domainAgeChecked = domainAgeChecked, domainAgeDays = domainAgeDays,
                    permissionsFound = permissionsFound
                )
            }

            val hasThreatIntel = safeBrowsingChecked || virusTotalChecked
            if (!hasThreatIntel) {
                score = score.coerceAtMost(HEURISTIC_ONLY_SCORE_CAP)
                reasons.add("No threat-intel source available. Score is heuristic-only")
            }

            SafetyResult(
                score = score, verdict = verdictForScore(score), reasons = reasons,
                safeBrowsingChecked = safeBrowsingChecked, safeBrowsingFlagged = false,
                virusTotalChecked = virusTotalChecked, virusTotalFlagged = false,
                domainAgeChecked = domainAgeChecked, domainAgeDays = domainAgeDays,
                permissionsFound = permissionsFound
            )
        }

    private fun verdictForScore(score: Int): Verdict = when {
        score >= 80 -> Verdict.SAFE
        score >= 50 -> Verdict.CAUTION
        else -> Verdict.UNSAFE
    }

    /**
     * Two short, plain-language sentences explaining what was actually
     * checked and why the domain got this verdict, meant to sit alongside
     * the score. Ported from the bot's describe_safety().
     */
    fun describeSafety(result: SafetyResult, domain: String): List<String> {
        val sentences = mutableListOf<String>()

        if (result.safeBrowsingChecked || result.virusTotalChecked) {
            val sources = mutableListOf<String>()
            if (result.safeBrowsingChecked) sources.add("Google Safe Browsing")
            if (result.virusTotalChecked) sources.add("VirusTotal")
            sentences.add(
                if (result.safeBrowsingFlagged || result.virusTotalFlagged) {
                    "${sources.joinToString(" and ")} has flagged this domain as a known threat."
                } else {
                    "${sources.joinToString(" and ")} found no known threats tied to this domain."
                }
            )
        } else {
            sentences.add(
                "No live threat database was available for this check, so the score is based only on " +
                    "the link's shape and the page's own content."
            )
        }

        val domainBits = mutableListOf<String>()
        if (result.domainAgeChecked && result.domainAgeDays != null) {
            domainBits.add(
                if (result.domainAgeDays < NEW_DOMAIN_RECENT_DAYS) {
                    "$domain was only registered ${result.domainAgeDays} day(s) ago"
                } else {
                    "$domain is an established domain"
                }
            )
        }
        if (result.permissionsFound.isNotEmpty()) {
            domainBits.add("the page does ask for ${result.permissionsFound.joinToString(", ")}")
        } else {
            domainBits.add("the page doesn't ask for camera, microphone, or location access")
        }

        if (domainBits.isNotEmpty()) {
            val joined = domainBits.joinToString(", and ")
            sentences.add(joined.replaceFirstChar { it.uppercase() } + ".")
        }

        return sentences
    }
}
