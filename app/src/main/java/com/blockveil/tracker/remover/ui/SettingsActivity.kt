package com.blockveil.tracker.remover.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.blockveil.tracker.remover.R
import com.blockveil.tracker.remover.TrackerRemoverApp
import com.blockveil.tracker.remover.data.SettingsRepository
import com.blockveil.tracker.remover.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        settings = (application as TrackerRemoverApp).settings
        loadCurrentValues()
        wireListeners()
    }

    private fun loadCurrentValues() {
        binding.switchSafeBrowsing.isChecked = settings.safeBrowsingEnabled
        binding.safeBrowsingKeyInput.setText(settings.safeBrowsingApiKey)
        binding.safeBrowsingKeyLayout.isEnabled = settings.safeBrowsingEnabled

        binding.switchVirusTotal.isChecked = settings.virusTotalEnabled
        binding.virusTotalKeyInput.setText(settings.virusTotalApiKey)
        binding.virusTotalKeyLayout.isEnabled = settings.virusTotalEnabled

        binding.switchDomainAge.isChecked = settings.domainAgeEnabled
        binding.switchSaveHistory.isChecked = settings.saveHistory
    }

    private fun wireListeners() {
        binding.switchSafeBrowsing.setOnCheckedChangeListener { _, isChecked ->
            settings.safeBrowsingEnabled = isChecked
            binding.safeBrowsingKeyLayout.isEnabled = isChecked
        }
        binding.safeBrowsingKeyInput.doAfterTextChanged {
            settings.safeBrowsingApiKey = it?.toString().orEmpty()
        }

        binding.switchVirusTotal.setOnCheckedChangeListener { _, isChecked ->
            settings.virusTotalEnabled = isChecked
            binding.virusTotalKeyLayout.isEnabled = isChecked
        }
        binding.virusTotalKeyInput.doAfterTextChanged {
            settings.virusTotalApiKey = it?.toString().orEmpty()
        }

        binding.switchDomainAge.setOnCheckedChangeListener { _, isChecked ->
            settings.domainAgeEnabled = isChecked
        }

        binding.switchSaveHistory.setOnCheckedChangeListener { _, isChecked ->
            settings.saveHistory = isChecked
        }

        binding.clearHistoryButton.setOnClickListener { confirmClearHistory() }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_clear_confirm_title)
            .setMessage(R.string.history_clear_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_clear) { _, _ ->
                lifecycleScope.launch {
                    (application as TrackerRemoverApp).database.cleanedLinkDao().clearAll()
                    Toast.makeText(this@SettingsActivity, R.string.settings_clear_history_done, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

}
