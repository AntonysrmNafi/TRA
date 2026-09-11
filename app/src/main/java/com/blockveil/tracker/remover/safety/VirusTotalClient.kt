package com.blockveil.tracker.remover.safety

import android.util.Base64
import com.blockveil.tracker.remover.network.HttpClientProvider
import okhttp3.Request
import org.json.JSONObject

/** VirusTotal v3 URL report. Needs the user's own API key (Settings). */
object VirusTotalClient {

    private const val ENDPOINT = "https://www.virustotal.com/api/v3/urls"

    data class VtResult(val malicious: Int, val suspicious: Int, val harmless: Int)

    fun check(url: String, apiKey: String): VtResult? {
        if (apiKey.isBlank()) return null

        // VirusTotal identifies a URL by base64url-encoding it, no padding.
        val urlId = Base64.encodeToString(
            url.toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

        val request = Request.Builder()
            .url("$ENDPOINT/$urlId")
            .header("x-apikey", apiKey)
            .get()
            .build()

        return runCatching {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                // 404 just means VT has no report for this URL yet: not an error, not a verdict.
                if (response.code == 404) return@use VtResult(0, 0, 0)
                if (!response.isSuccessful) return@use null

                val text = response.body?.string() ?: return@use null
                val stats = JSONObject(text)
                    .getJSONObject("data")
                    .getJSONObject("attributes")
                    .getJSONObject("last_analysis_stats")

                VtResult(
                    malicious = stats.optInt("malicious", 0),
                    suspicious = stats.optInt("suspicious", 0),
                    harmless = stats.optInt("harmless", 0)
                )
            }
        }.getOrNull()
    }
}
