package com.blockveil.tracker.remover.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blockveil.tracker.remover.R
import com.blockveil.tracker.remover.data.CleanedLinkEntity
import com.blockveil.tracker.remover.databinding.ActivityHistoryDetailBinding
import com.blockveil.tracker.remover.safety.Verdict
import java.text.DateFormat
import java.util.Date

/** Full detail view for one history entry: original link, cleaned link, every tracker removed, and why. */
class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val original = intent.getStringExtra(EXTRA_ORIGINAL).orEmpty()
        val cleaned = intent.getStringExtra(EXTRA_CLEANED).orEmpty()
        val domain = intent.getStringExtra(EXTRA_DOMAIN).orEmpty()
        val trackerNames = intent.getStringExtra(EXTRA_TRACKER_NAMES).orEmpty()
        val trackerCount = intent.getIntExtra(EXTRA_TRACKER_COUNT, 0)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val verdict = runCatching { Verdict.valueOf(intent.getStringExtra(EXTRA_VERDICT).orEmpty()) }
            .getOrDefault(Verdict.UNKNOWN)
        val safetyScore = intent.getIntExtra(EXTRA_SAFETY_SCORE, -1).takeIf { it >= 0 }
        val timestampMillis = intent.getLongExtra(EXTRA_TIMESTAMP, 0L)

        binding.toolbar.title = domain.ifBlank { getString(R.string.history_title) }
        binding.originalUrlText.text = original
        binding.cleanedUrlText.text = cleaned

        val (verdictLabelRes, verdictColorRes) = when (verdict) {
            Verdict.SAFE -> R.string.safety_safe to R.color.bv_safe
            Verdict.CAUTION -> R.string.safety_caution to R.color.bv_suspicious
            Verdict.UNSAFE -> R.string.safety_unsafe to R.color.bv_malicious
            Verdict.UNKNOWN -> R.string.safety_unknown to R.color.bv_unknown
        }
        binding.verdictBadge.text = if (safetyScore != null) {
            "${getString(verdictLabelRes)} ($safetyScore/100)"
        } else {
            getString(verdictLabelRes)
        }
        binding.verdictBadge.setBackgroundColor(getColor(verdictColorRes))

        binding.removedParamsText.text = if (trackerCount == 0) {
            getString(R.string.label_no_trackers)
        } else {
            getString(R.string.label_removed_params, trackerCount) + ": " + trackerNames
        }

        if (description.isNullOrBlank()) {
            binding.descriptionLabel.visibility = android.view.View.GONE
            binding.descriptionText.visibility = android.view.View.GONE
        } else {
            binding.descriptionLabel.visibility = android.view.View.VISIBLE
            binding.descriptionText.visibility = android.view.View.VISIBLE
            binding.descriptionText.text = description
        }

        binding.timestampText.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(timestampMillis))
    }

    companion object {
        private const val EXTRA_ORIGINAL = "extra_original"
        private const val EXTRA_CLEANED = "extra_cleaned"
        private const val EXTRA_DOMAIN = "extra_domain"
        private const val EXTRA_TRACKER_NAMES = "extra_tracker_names"
        private const val EXTRA_TRACKER_COUNT = "extra_tracker_count"
        private const val EXTRA_DESCRIPTION = "extra_description"
        private const val EXTRA_VERDICT = "extra_verdict"
        private const val EXTRA_SAFETY_SCORE = "extra_safety_score"
        private const val EXTRA_TIMESTAMP = "extra_timestamp"

        fun launch(context: Context, entity: CleanedLinkEntity) {
            context.startActivity(
                Intent(context, HistoryDetailActivity::class.java)
                    .putExtra(EXTRA_ORIGINAL, entity.original)
                    .putExtra(EXTRA_CLEANED, entity.cleaned)
                    .putExtra(EXTRA_DOMAIN, entity.domain)
                    .putExtra(EXTRA_TRACKER_NAMES, entity.removedParamsNames)
                    .putExtra(EXTRA_TRACKER_COUNT, entity.removedParamsCount)
                    .putExtra(EXTRA_DESCRIPTION, entity.description)
                    .putExtra(EXTRA_VERDICT, entity.verdict)
                    .apply { entity.safetyScore?.let { putExtra(EXTRA_SAFETY_SCORE, it) } }
                    .putExtra(EXTRA_TIMESTAMP, entity.timestampMillis)
            )
        }
    }
}
