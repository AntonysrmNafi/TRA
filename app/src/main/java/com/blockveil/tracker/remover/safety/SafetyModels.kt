package com.blockveil.tracker.remover.safety

/**
 * SAFE/CAUTION/UNSAFE mirror the bot's three-tier verdict exactly.
 * UNKNOWN is Android-side only, meaning "no safety check ran at all"
 * (every check toggle off in Settings) — SafetyChecker itself never
 * produces UNKNOWN, only SAFE/CAUTION/UNSAFE.
 */
enum class Verdict { SAFE, CAUTION, UNSAFE, UNKNOWN }

data class SafetyResult(
    val score: Int, // 0-100, higher is safer
    val verdict: Verdict,
    val reasons: List<String> = emptyList(),
    val safeBrowsingChecked: Boolean = false,
    val safeBrowsingFlagged: Boolean = false,
    val virusTotalChecked: Boolean = false,
    val virusTotalFlagged: Boolean = false,
    val domainAgeChecked: Boolean = false,
    val domainAgeDays: Int? = null,
    val permissionsFound: List<String> = emptyList()
)
