package com.blockveil.tracker.remover.safety

enum class Verdict { SAFE, SUSPICIOUS, MALICIOUS, UNKNOWN }

data class SafetyResult(
    val verdict: Verdict,
    val reasons: List<String>,
    val domainAgeDays: Int? = null
)
