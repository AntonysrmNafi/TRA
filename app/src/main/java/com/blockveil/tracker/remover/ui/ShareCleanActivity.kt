package com.blockveil.tracker.remover.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blockveil.tracker.remover.R
import com.blockveil.tracker.remover.TrackerRemoverApp
import com.blockveil.tracker.remover.databinding.ActivityShareCleanBinding
import com.blockveil.tracker.remover.util.LinkProcessor
import com.blockveil.tracker.remover.util.NetworkUtils
import com.blockveil.tracker.remover.util.trackerCount
import kotlinx.coroutines.launch

/**
 * The "select this app from the Share sheet" target. This screen is
 * deliberately invisible (transparent, no dim, no animation, no UI of any
 * kind) — it cleans the shared link (trackers stripped, shorteners
 * resolved) and immediately re-opens the Share sheet with the cleaned
 * link, so sharing never visibly looks like this app opened at all.
 *
 * Safety checks are skipped here on purpose — this path is about being
 * instant. The normal paste-a-link screen in MainActivity still runs
 * them when Settings has them turned on.
 */
class ShareCleanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareCleanBinding
    private val app: TrackerRemoverApp by lazy { application as TrackerRemoverApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareCleanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, R.string.share_clean_no_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, R.string.no_internet_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            when (val result = LinkProcessor.process(sharedText, app.settings, skipSafetyCheck = true)) {
                is LinkProcessor.ProcessResult.NoLinkFound -> {
                    Toast.makeText(this@ShareCleanActivity, R.string.share_clean_no_link, Toast.LENGTH_SHORT).show()
                }
                is LinkProcessor.ProcessResult.Success -> {
                    if (app.settings.saveHistory) {
                        val entity = LinkProcessor.toEntity(result.link, getString(R.string.label_short_url_resolved))
                        app.database.cleanedLinkDao().insert(entity)
                    }
                    app.settings.recordCleanedLink(result.link.trackerCount)
                    reshare(result.link.cleaned)
                }
            }
            finish()
        }
    }

    private fun reshare(cleanedUrl: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, cleanedUrl)
        }
        val chooser = Intent.createChooser(shareIntent, getString(R.string.share_clean_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(chooser)
    }
}
