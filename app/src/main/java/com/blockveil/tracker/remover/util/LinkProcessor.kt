package com.blockveil.tracker.remover.util

import com.blockveil.tracker.remover.cleaner.LinkCleaner
import com.blockveil.tracker.remover.data.CleanedLinkEntity
import com.blockveil.tracker.remover.data.SettingsRepository
import com.blockveil.tracker.remover.network.UrlResolver
import com.blockveil.tracker.remover.safety.SafetyChecker
import com.blockveil.tracker.remover.safety.SafetyResult
import com.blockveil.tracker.remover.safety.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full pipeline for one pasted/shared piece of text:
 * find link -> resolve shortener -> strip trackers -> (optional) safety check.
 * All network/DB work happens off the main thread.
 */
object LinkProcessor {

    data class ProcessedLink(
        val original: String,
        val cleaned: String,
        val removedParams: List<String>,
        val domain: String,
        val wasShortenerResolved: Boolean,
        val trackerDescription: String?,
        val safety: SafetyResult?
    )

    sealed class ProcessResult {
        data class Success(val link: ProcessedLink) : ProcessResult()
        object NoLinkFound : ProcessResult()
    }

    /**
     * @param skipSafetyCheck true for the instant share-and-reshare flow,
     *   where the point is speed — safety checks add a network round trip
     *   the user didn't ask for in that path.
     */
    suspend fun process(
        inputText: String,
        settings: SettingsRepository,
        skipSafetyCheck: Boolean = false
    ): ProcessResult =
        withContext(Dispatchers.IO) {
            val rawUrl = LinkCleaner.extractFirstUrl(inputText)
                ?: return@withContext ProcessResult.NoLinkFound

            var workingUrl = rawUrl
            var resolved = false

            val quickCheck = LinkCleaner.clean(rawUrl)
            if (quickCheck.isShortener) {
                val resolution = UrlResolver.resolveFinalUrl(rawUrl)
                if (resolution is UrlResolver.Result.Resolved && resolution.hops > 0) {
                    workingUrl = resolution.finalUrl
                    resolved = true
                }
            }

            val finalClean = LinkCleaner.clean(workingUrl)

            val safety = if (!skipSafetyCheck && settings.anySafetyCheckEnabled()) {
                SafetyChecker.check(finalClean.cleaned, finalClean.domain, settings)
            } else null

            ProcessResult.Success(
                ProcessedLink(
                    original = rawUrl,
                    cleaned = finalClean.cleaned,
                    removedParams = finalClean.removedParams,
                    domain = finalClean.domain,
                    wasShortenerResolved = resolved,
                    trackerDescription = LinkCleaner.describeRemovedTrackers(finalClean.removedParams, resolved),
                    safety = safety
                )
            )
        }

    fun toEntity(link: ProcessedLink): CleanedLinkEntity = CleanedLinkEntity(
        original = link.original,
        cleaned = link.cleaned,
        domain = link.domain,
        removedParamsCount = link.removedParams.size + if (link.wasShortenerResolved) 1 else 0,
        verdict = (link.safety?.verdict ?: Verdict.UNKNOWN).name,
        timestampMillis = System.currentTimeMillis()
    )
}
