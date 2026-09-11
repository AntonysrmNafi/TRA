package com.blockveil.tracker.remover.safety

import com.blockveil.tracker.remover.network.HttpClientProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Google Safe Browsing v4 lookup. Needs the user's own API key (Settings). */
object SafeBrowsingClient {

    private const val ENDPOINT = "https://safebrowsing.googleapis.com/v4/threatMatches:find"

    /** Null return means "couldn't check" (no key, network error, bad response) — never treat as unsafe. */
    fun check(url: String, apiKey: String): Boolean? {
        if (apiKey.isBlank()) return null

        val body = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientId", "com.blockveil.tracker.remover")
                put("clientVersion", "1.0.0")
            })
            put("threatInfo", JSONObject().apply {
                put("threatTypes", JSONArray(listOf(
                    "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"
                )))
                put("platformTypes", JSONArray(listOf("ANY_PLATFORM")))
                put("threatEntryTypes", JSONArray(listOf("URL")))
                put("threatEntries", JSONArray(listOf(JSONObject().put("url", url))))
            })
        }

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                // An empty object ({}) means no match was found, i.e. link is clean.
                JSONObject(text).has("matches")
            }
        }.getOrNull()
    }
}
