package com.blockveil.tracker.remover.safety

import com.blockveil.tracker.remover.data.SettingsRepository

/**
 * Runs whichever checks are turned on in Settings and combines them into
 * one verdict. Every individual check fails soft: a missing key or a
 * network error just means "skip this check", never "mark as unsafe".
 */
object SafetyChecker {

    fun check(url: String, domain: String, settings: SettingsRepository): SafetyResult {
        val reasons = mutableListOf<String>()
        var malicious = false
        var suspicious = false
        var domainAgeDays: Int? = null
        var anyCheckRan = false

        if (settings.safeBrowsingEnabled) {
            val key = settings.safeBrowsingApiKey
            val matched = SafeBrowsingClient.check(url, key)
            if (matched != null) {
                anyCheckRan = true
                if (matched) {
                    malicious = true
                    reasons.add("Flagged by Google Safe Browsing")
                }
            }
        }

        if (settings.virusTotalEnabled) {
            val key = settings.virusTotalApiKey
            val result = VirusTotalClient.check(url, key)
            if (result != null) {
                anyCheckRan = true
                if (result.malicious > 0) {
                    malicious = true
                    reasons.add("${result.malicious} antivirus engines flagged this on VirusTotal")
                } else if (result.suspicious > 0) {
                    suspicious = true
                    reasons.add("${result.suspicious} engines marked this as suspicious on VirusTotal")
                }
            }
        }

        if (settings.domainAgeEnabled) {
            domainAgeDays = DomainAgeClient.lookupAgeDays(domain)
            if (domainAgeDays != null) {
                anyCheckRan = true
                if (domainAgeDays < 30) {
                    suspicious = true
                    reasons.add("Domain registered only $domainAgeDays days ago")
                }
            }
        }

        val verdict = when {
            malicious -> Verdict.MALICIOUS
            suspicious -> Verdict.SUSPICIOUS
            anyCheckRan -> Verdict.SAFE
            else -> Verdict.UNKNOWN
        }

        return SafetyResult(verdict, reasons, domainAgeDays)
    }
}
