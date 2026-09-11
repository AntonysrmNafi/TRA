package com.blockveil.tracker.remover.safety

import com.blockveil.tracker.remover.network.HttpClientProvider
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Free domain-age lookup via RDAP (rdap.org), no API key required. Brand new
 * domains are a common phishing signal, so this is worth flagging even
 * without the paid checks turned on.
 */
object DomainAgeClient {

    /** Returns age in days, or null if the lookup failed / RDAP has no record. */
    fun lookupAgeDays(domain: String): Int? {
        val request = Request.Builder()
            .url("https://rdap.org/domain/$domain")
            .build()

        return runCatching {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                val events = JSONObject(text).optJSONArray("events") ?: return@use null
                val registrationDate = findEventDate(events, "registration") ?: return@use null
                ChronoUnit.DAYS.between(Instant.parse(registrationDate), Instant.now()).toInt()
            }
        }.getOrNull()
    }

    private fun findEventDate(events: JSONArray, action: String): String? {
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            if (event.optString("eventAction") == action) {
                return event.optString("eventDate").takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
