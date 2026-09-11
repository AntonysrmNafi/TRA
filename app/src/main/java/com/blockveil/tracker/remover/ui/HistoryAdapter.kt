package com.blockveil.tracker.remover.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.blockveil.tracker.remover.data.CleanedLinkEntity
import com.blockveil.tracker.remover.databinding.ItemHistoryBinding
import com.blockveil.tracker.remover.safety.Verdict
import java.text.DateFormat
import java.util.Date

class HistoryAdapter : ListAdapter<CleanedLinkEntity, HistoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CleanedLinkEntity) {
            binding.domainText.text = item.domain.ifBlank { "unknown domain" }
            binding.cleanedText.text = item.cleaned

            val verdict = runCatching { Verdict.valueOf(item.verdict) }.getOrDefault(Verdict.UNKNOWN)
            val (label, colorAttr) = when (verdict) {
                Verdict.SAFE -> "Safe" to com.blockveil.tracker.remover.R.color.bv_safe
                Verdict.SUSPICIOUS -> "Suspicious" to com.blockveil.tracker.remover.R.color.bv_suspicious
                Verdict.MALICIOUS -> "Unsafe" to com.blockveil.tracker.remover.R.color.bv_malicious
                Verdict.UNKNOWN -> "Not checked" to com.blockveil.tracker.remover.R.color.bv_unknown
            }
            binding.verdictBadge.text = label
            binding.verdictBadge.setBackgroundColor(binding.root.context.getColor(colorAttr))

            val when_ = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(item.timestampMillis))
            val trackers = if (item.removedParamsCount == 0) {
                "no trackers"
            } else {
                "${item.removedParamsCount} tracker(s) removed"
            }
            binding.metaText.text = "$when_  •  $trackers"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CleanedLinkEntity>() {
            override fun areItemsTheSame(old: CleanedLinkEntity, new: CleanedLinkEntity) = old.id == new.id
            override fun areContentsTheSame(old: CleanedLinkEntity, new: CleanedLinkEntity) = old == new
        }
    }
}
