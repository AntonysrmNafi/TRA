package com.blockveil.tracker.remover.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blockveil.tracker.remover.databinding.ActivityTextDocumentBinding

/**
 * One reusable screen for the static documents in Settings > Other
 * (Privacy Policy, Terms and Conditions, Data collection, About). Avoids
 * near-identical activities for what's really just "title + long text".
 */
class TextDocumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextDocumentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.toolbar.title = intent.getStringExtra(EXTRA_TITLE)
        binding.documentText.text = intent.getStringExtra(EXTRA_CONTENT)
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_CONTENT = "extra_content"

        fun launch(context: Context, title: String, content: String) {
            context.startActivity(
                Intent(context, TextDocumentActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_CONTENT, content)
            )
        }
    }
}
