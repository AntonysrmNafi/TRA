package com.blockveil.tracker.remover.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blockveil.tracker.remover.R
import com.blockveil.tracker.remover.TrackerRemoverApp
import com.blockveil.tracker.remover.databinding.ActivityShareCleanBinding
import com.blockveil.tracker.remover.util.LinkProcessor
import com.blockveil.tracker.remover.util.NetworkUtils
import com.blockveil.tracker.remover.widget.WidgetUpdater
import kotlinx.coroutines.launch

/**
 * Tapped from the home-screen widget's "Clean" button. Same invisible-
 * trampoline pattern as ShareCleanActivity (see there for why): reads the
 * clipboard, cleans it, copies the result back to the clipboard, and shows
 * one short Toast, all without visibly opening the app.
 *
 * Clipboard reading is deliberately done in onWindowFocusChanged(), not
 * onCreate(). Since Android 10, an app can only read the clipboard once its
 * window actually has focus; reading it in onCreate() runs before that
 * happens and silently returns nothing, which is exactly why the widget
 * kept reporting an empty clipboard even with a link freshly copied.
 */
class WidgetCleanActivity : AppCompatActivity() {

    private val app: TrackerRemoverApp by lazy { application as TrackerRemoverApp }
    private var hasStartedProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityShareCleanBinding.inflate(layoutInflater).root)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || hasStartedProcessing) return
        hasStartedProcessing = true
        processClipboard()
    }

    private fun processClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

        if (clipText.isNullOrBlank()) {
            Toast.makeText(this, R.string.widget_clipboard_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, R.string.no_internet_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            when (val result = LinkProcessor.process(clipText, app.settings, skipSafetyCheck = true)) {
                is LinkProcessor.ProcessResult.NoLinkFound -> {
                    Toast.makeText(this@WidgetCleanActivity, R.string.share_clean_no_link, Toast.LENGTH_SHORT).show()
                }
                is LinkProcessor.ProcessResult.Success -> {
                    val labels = LinkProcessor.ResolutionLabels(
                        shortUrlResolved = getString(R.string.label_short_url_resolved),
                        shortUrlFailed = getString(R.string.label_short_url_failed),
                        ampResolved = getString(R.string.label_amp_resolved),
                        ampFailed = getString(R.string.label_amp_failed)
                    )
                    val displayNames = LinkProcessor.displayTrackerNames(result.link, labels)
                    if (app.settings.saveHistory) {
                        app.database.cleanedLinkDao().insert(LinkProcessor.toEntity(result.link, labels))
                    }
                    app.settings.recordCleanedLink(displayNames.size)
                    copyToClipboard(result.link.cleaned)
                    WidgetUpdater.updateAll(this@WidgetCleanActivity)
                    Toast.makeText(
                        this@WidgetCleanActivity,
                        getString(R.string.widget_clean_success, result.link.domain),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            finish()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Cleaned link", text))
    }
}
