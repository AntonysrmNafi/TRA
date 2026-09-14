package com.blockveil.tracker.remover.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockveil.tracker.remover.R
import com.blockveil.tracker.remover.TrackerRemoverApp
import com.blockveil.tracker.remover.databinding.ActivityMainBinding
import com.blockveil.tracker.remover.safety.SafetyChecker
import com.blockveil.tracker.remover.safety.Verdict
import com.blockveil.tracker.remover.util.LinkProcessor
import com.blockveil.tracker.remover.util.NetworkUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentCleanedUrl: String? = null
    private val recentAdapter = HistoryAdapter { entity -> HistoryDetailActivity.launch(this, entity) }

    private val app: TrackerRemoverApp by lazy { application as TrackerRemoverApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cleanButton.setOnClickListener { cleanCurrentInput() }
        binding.copyButton.setOnClickListener { copyResult() }
        binding.shareButton.setOnClickListener { shareResult() }
        binding.openButton.setOnClickListener { openResult() }
        binding.pasteButton.setOnClickListener { pasteFromClipboard() }
        binding.seeAllText.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.historyTile.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.settingsTile.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.recentList.layoutManager = LinearLayoutManager(this)
        binding.recentList.adapter = recentAdapter

        // Hide the previous result as soon as the user edits the text again,
        // so a stale "cleaned" card never sits next to different input.
        binding.linkInput.doOnTextChanged { _, _, _, _ -> binding.resultCard.visibility = android.view.View.GONE }

        observeRecentHistory()
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        // Covers the case where a link was cleaned elsewhere (e.g. the
        // instant share-and-reshare flow) while this screen was paused.
        refreshStats()
    }

    /** Reads the two lifetime counters (links cleaned, trackers removed) into the bento stat tiles. */
    private fun refreshStats() {
        binding.statLinksCountText.text = app.settings.totalLinksCleaned.toString()
        binding.statTrackersCountText.text = app.settings.totalTrackersRemoved.toString()
    }

    /** Keeps the "Recent" strip on the main screen in sync with history, only while visible. */
    private fun observeRecentHistory() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.database.cleanedLinkDao().observeAll().collect { items ->
                    val recentThree = items.take(3)
                    recentAdapter.submitList(recentThree)
                    binding.recentSection.visibility = if (recentThree.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                }
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
        if (clipText.isNullOrBlank()) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        binding.linkInput.setText(clipText)
        binding.linkInput.setSelection(clipText.length)
    }

    private fun cleanCurrentInput() {
        val text = binding.linkInput.text?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.empty_input_error, Toast.LENGTH_SHORT).show()
            return
        }
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, R.string.no_internet_error, Toast.LENGTH_LONG).show()
            return
        }

        setLoading(true, R.string.label_resolving)

        lifecycleScope.launch {
            when (val result = LinkProcessor.process(text, app.settings)) {
                is LinkProcessor.ProcessResult.NoLinkFound -> {
                    setLoading(false)
                    Toast.makeText(this@MainActivity, R.string.no_link_found_error, Toast.LENGTH_SHORT).show()
                }
                is LinkProcessor.ProcessResult.Success -> {
                    val labels = resolutionLabels()
                    val displayNames = LinkProcessor.displayTrackerNames(result.link, labels)
                    if (app.settings.saveHistory) {
                        val entity = LinkProcessor.toEntity(result.link, labels)
                        app.database.cleanedLinkDao().insert(entity)
                    }
                    app.settings.recordCleanedLink(displayNames.size)
                    refreshStats()
                    setLoading(false)
                    showResult(result.link)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean, statusRes: Int? = null) {
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.statusText.visibility = if (loading && statusRes != null) android.view.View.VISIBLE else android.view.View.GONE
        statusRes?.let { binding.statusText.setText(it) }
        binding.cleanButton.isEnabled = !loading
    }

    private fun resolutionLabels() = LinkProcessor.ResolutionLabels(
        shortUrlResolved = getString(R.string.label_short_url_resolved),
        shortUrlFailed = getString(R.string.label_short_url_failed),
        ampResolved = getString(R.string.label_amp_resolved),
        ampFailed = getString(R.string.label_amp_failed)
    )

    private fun showResult(link: LinkProcessor.ProcessedLink) {
        currentCleanedUrl = link.cleaned
        binding.resultCard.visibility = android.view.View.VISIBLE
        binding.cleanedUrlText.text = link.cleaned

        val displayItems = LinkProcessor.displayTrackerNames(link, resolutionLabels())

        binding.removedParamsText.text = if (displayItems.isEmpty()) {
            getString(R.string.label_no_trackers)
        } else {
            getString(R.string.label_removed_params, displayItems.size) + ": " + displayItems.joinToString(", ")
        }

        val safety = link.safety
        val safetyDescription = if (safety != null) SafetyChecker.describeSafety(safety, link.domain) else emptyList()
        val combinedDescription = listOfNotNull(link.trackerDescription) + safetyDescription

        if (combinedDescription.isEmpty()) {
            binding.descriptionLabel.visibility = android.view.View.GONE
            binding.descriptionText.visibility = android.view.View.GONE
        } else {
            binding.descriptionLabel.visibility = android.view.View.VISIBLE
            binding.descriptionText.visibility = android.view.View.VISIBLE
            binding.descriptionText.text = combinedDescription.joinToString(" ")
        }

        if (safety == null) {
            binding.safetyBadge.visibility = android.view.View.GONE
            binding.safetyReasonsText.visibility = android.view.View.GONE
        } else {
            binding.safetyBadge.visibility = android.view.View.VISIBLE
            val (labelRes, colorRes) = when (safety.verdict) {
                Verdict.SAFE -> R.string.safety_safe to R.color.bv_safe
                Verdict.CAUTION -> R.string.safety_caution to R.color.bv_suspicious
                Verdict.UNSAFE -> R.string.safety_unsafe to R.color.bv_malicious
                Verdict.UNKNOWN -> R.string.safety_unknown to R.color.bv_unknown
            }
            binding.safetyBadge.text = "${getString(labelRes)} (${safety.score}/100)"
            binding.safetyBadge.setBackgroundColor(getColor(colorRes))

            if (safety.reasons.isEmpty()) {
                binding.safetyReasonsText.visibility = android.view.View.GONE
            } else {
                binding.safetyReasonsText.visibility = android.view.View.VISIBLE
                binding.safetyReasonsText.text = safety.reasons.joinToString("\n") { "• $it" }
            }
        }
    }

    private fun copyResult() {
        val url = currentCleanedUrl ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Cleaned link", url))
        Toast.makeText(this, R.string.action_copy, Toast.LENGTH_SHORT).show()
    }

    private fun shareResult() {
        val url = currentCleanedUrl ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }

    private fun openResult() {
        val url = currentCleanedUrl ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
        }
    }
}
