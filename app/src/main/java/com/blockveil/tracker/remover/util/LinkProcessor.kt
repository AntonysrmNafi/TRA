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
 * Full pipeline for one pasted/shared piece of text, mirroring the bot's
 * process_url: find link -> decide how it needs resolving (confirmed
 * shortener, AMP link, or neither) -> resolve -> strip trackers from the
 * resolved link -> (optional) safety check.
 * All network/DB work happens off the main thread.
 */
object LinkProcessor {

    data class ProcessedLink(
        val original: String,
        val cleaned: String,
        val removedParams: List<String>,
        val domain: String,
        /** True if resolving actually changed the URL (redirect followed, or AMP unwrapped). */
        val wasRedirected: Boolean,
        /** True if this looked enough like a shortener/AMP link to be worth trying, even if it didn't pan out. */
        val attemptedResolution: Boolean,
        val resolutionKind: LinkCleaner.ResolutionKind,
        val trackerDescription: String?,
        val safety: SafetyResult?
    )

    sealed class ProcessResult {
        data class Success(val link: ProcessedLink) : ProcessResult()
        object NoLinkFound : ProcessResult()
    }

    /** The four possible "what happened with resolution" notes, supplied by the caller since they're string resources. */
    data class ResolutionLabels(
        val shortUrlResolved: String,
        val shortUrlFailed: String,
        val ampResolved: String,
        val ampFailed: String
    )

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

            val decision = LinkCleaner.decideResolution(rawUrl)

            var workingUrl = rawUrl
            var wasRedirected = false

            when {
                decision.directResolvedUrl != null -> {
                    // Google AMP viewer link: the real URL is embedded in this
                    // one's own path, no network call needed to find it.
                    workingUrl = decision.directResolvedUrl
                    wasRedirected = workingUrl != rawUrl
                }
                decision.kind != LinkCleaner.ResolutionKind.NONE -> {
                    val resolution = UrlResolver.resolveFinalUrl(rawUrl)
                    if (resolution is UrlResolver.Result.Resolved && resolution.hops > 0) {
                        workingUrl = resolution.finalUrl
                        wasRedirected = true
                    }
                }
                else -> Unit
            }

            val finalClean = LinkCleaner.clean(workingUrl)
            val attemptedResolution = decision.confirmedShortener || decision.kind == LinkCleaner.ResolutionKind.AMP

            val safety = if (!skipSafetyCheck && settings.anySafetyCheckEnabled()) {
                SafetyChecker.check(rawUrl, finalClean.cleaned, finalClean.domain, settings)
            } else null

            ProcessResult.Success(
                ProcessedLink(
                    original = rawUrl,
                    cleaned = finalClean.cleaned,
                    removedParams = finalClean.removedParams,
                    domain = finalClean.domain,
                    wasRedirected = wasRedirected,
                    attemptedResolution = attemptedResolution,
                    resolutionKind = decision.kind,
                    trackerDescription = LinkCleaner.describeRemovedTrackers(
                        finalClean.removedParams,
                        wasRedirected,
                        decision.kind
                    ),
                    safety = safety
                )
            )
        }

    /**
     * The full list shown to the user: tracker param names, plus a note
     * about what happened with resolution — whether it succeeded
     * ("Short URL (resolved)" / "AMP link (unwrapped to original)") or was
     * attempted but didn't change anything ("...could not verify/find...").
     * No note at all if resolution was never attempted.
     */
    fun displayTrackerNames(link: ProcessedLink, labels: ResolutionLabels): List<String> {
        val note = when {
            link.wasRedirected && link.resolutionKind == LinkCleaner.ResolutionKind.AMP -> labels.ampResolved
            link.wasRedirected -> labels.shortUrlResolved
            link.attemptedResolution && link.resolutionKind == LinkCleaner.ResolutionKind.AMP -> labels.ampFailed
            link.attemptedResolution -> labels.shortUrlFailed
            else -> null
        }
        return if (note != null) link.removedParams + note else link.removedParams
    }

    fun toEntity(link: ProcessedLink, labels: ResolutionLabels): CleanedLinkEntity {
        val displayNames = displayTrackerNames(link, labels)
        return CleanedLinkEntity(
            original = link.original,
            cleaned = link.cleaned,
            domain = link.domain,
            removedParamsCount = displayNames.size,
            removedParamsNames = displayNames.joinToString(","),
            description = link.trackerDescription,
            verdict = (link.safety?.verdict ?: Verdict.UNKNOWN).name,
            safetyScore = link.safety?.score,
            timestampMillis = System.currentTimeMillis()
        )
    }
}
