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
import com.blockveil.tracker.remover.safety.Verdict
import com.blockveil.tracker.remover.util.LinkProcessor
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentCleanedUrl: String? = null
    private val recentAdapter = HistoryAdapter()

    private val app: TrackerRemoverApp by lazy { application as TrackerRemoverApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Deliberately NOT calling setSupportActionBar(binding.toolbar) here:
        // doing so hands menu control to the ActionBar's onCreateOptionsMenu
        // (which we don't override), and it wipes out the icons set via the
        // toolbar's app:menu XML attribute. Managing the toolbar's menu
        // directly avoids that conflict.
        binding.toolbar.inflateMenu(R.menu.menu_main)

        binding.cleanButton.setOnClickListener { cleanCurrentInput() }
        binding.copyButton.setOnClickListener { copyResult() }
        binding.shareButton.setOnClickListener { shareResult() }
        binding.openButton.setOnClickListener { openResult() }
        binding.pasteButton.setOnClickListener { pasteFromClipboard() }
        binding.seeAllText.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.recentList.layoutManager = LinearLayoutManager(this)
        binding.recentList.adapter = recentAdapter

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java)); true
                }
                R.id.action_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java)); true
                }
                else -> false
            }
        }

        // Hide the previous result as soon as the user edits the text again,
        // so a stale "cleaned" card never sits next to different input.
        binding.linkInput.doOnTextChanged { _, _, _, _ -> binding.resultCard.visibility = android.view.View.GONE }

        observeRecentHistory()
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

        setLoading(true, R.string.label_resolving)

        lifecycleScope.launch {
            when (val result = LinkProcessor.process(text, app.settings)) {
                is LinkProcessor.ProcessResult.NoLinkFound -> {
                    setLoading(false)
                    Toast.makeText(this@MainActivity, R.string.no_link_found_error, Toast.LENGTH_SHORT).show()
                }
                is LinkProcessor.ProcessResult.Success -> {
                    if (app.settings.saveHistory) {
                        app.database.cleanedLinkDao().insert(LinkProcessor.toEntity(result.link))
                    }
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

    private fun showResult(link: LinkProcessor.ProcessedLink) {
        currentCleanedUrl = link.cleaned
        binding.resultCard.visibility = android.view.View.VISIBLE
        binding.cleanedUrlText.text = link.cleaned

        val displayItems = link.removedParams.toMutableList()
        if (link.wasShortenerResolved) displayItems.add(getString(R.string.label_short_url_resolved))

        binding.removedParamsText.text = if (displayItems.isEmpty()) {
            getString(R.string.label_no_trackers)
        } else {
            getString(R.string.label_removed_params, displayItems.size) + ": " + displayItems.joinToString(", ")
        }

        if (link.trackerDescription.isNullOrBlank()) {
            binding.descriptionLabel.visibility = android.view.View.GONE
            binding.descriptionText.visibility = android.view.View.GONE
        } else {
            binding.descriptionLabel.visibility = android.view.View.VISIBLE
            binding.descriptionText.visibility = android.view.View.VISIBLE
            binding.descriptionText.text = link.trackerDescription
        }

        val safety = link.safety
        if (safety == null) {
            binding.safetyBadge.visibility = android.view.View.GONE
            binding.safetyReasonsText.visibility = android.view.View.GONE
        } else {
            binding.safetyBadge.visibility = android.view.View.VISIBLE
            val (labelRes, colorRes) = when (safety.verdict) {
                Verdict.SAFE -> R.string.safety_safe to R.color.bv_safe
                Verdict.SUSPICIOUS -> R.string.safety_suspicious to R.color.bv_suspicious
                Verdict.MALICIOUS -> R.string.safety_malicious to R.color.bv_malicious
                Verdict.UNKNOWN -> R.string.safety_unknown to R.color.bv_unknown
            }
            binding.safetyBadge.text = getString(labelRes)
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
