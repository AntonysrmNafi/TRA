package com.blockveil.tracker.remover.ui

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockveil.tracker.remover.R
import com.blockveil.tracker.remover.TrackerRemoverApp
import com.blockveil.tracker.remover.data.CleanedLinkEntity
import com.blockveil.tracker.remover.databinding.ActivityHistoryBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var app: TrackerRemoverApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        app = application as TrackerRemoverApp

        val adapter = HistoryAdapter { entity -> HistoryDetailActivity.launch(this, entity) }
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter

        ItemTouchHelper(SwipeToDeleteCallback(adapter)).attachToRecyclerView(binding.historyList)

        lifecycleScope.launch {
            app.database.cleanedLinkDao().observeAll().collect { items ->
                adapter.submitList(items)
                binding.emptyText.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    /** Swipe left or right on one row to delete just that entry, with an Undo snackbar. */
    private inner class SwipeToDeleteCallback(
        private val adapter: HistoryAdapter
    ) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START or ItemTouchHelper.END) {

        private val deleteBackground = ColorDrawable(ContextCompat.getColor(this@HistoryActivity, R.color.bv_malicious))
        private val deleteIcon = ContextCompat.getDrawable(this@HistoryActivity, android.R.drawable.ic_menu_delete)?.apply {
            setTint(ContextCompat.getColor(this@HistoryActivity, R.color.white))
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ) = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val removed = adapter.currentList[position]
            deleteEntry(removed)
        }

        private fun deleteEntry(entry: CleanedLinkEntity) {
            lifecycleScope.launch { app.database.cleanedLinkDao().delete(entry) }
            Snackbar.make(binding.root, R.string.history_item_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo) {
                    // Re-insert with id reset to 0 so Room assigns a fresh
                    // auto-generated id rather than colliding with the old one.
                    lifecycleScope.launch { app.database.cleanedLinkDao().insert(entry.copy(id = 0)) }
                }
                .show()
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            val itemView = viewHolder.itemView
            deleteBackground.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
            deleteBackground.draw(c)

            deleteIcon?.let { icon ->
                val margin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + margin
                val iconBottom = iconTop + icon.intrinsicHeight
                when {
                    dX > 0 -> {
                        val left = itemView.left + margin
                        icon.setBounds(left, iconTop, left + icon.intrinsicWidth, iconBottom)
                    }
                    dX < 0 -> {
                        val right = itemView.right - margin
                        icon.setBounds(right - icon.intrinsicWidth, iconTop, right, iconBottom)
                    }
                    else -> icon.setBounds(0, 0, 0, 0)
                }
                icon.draw(c)
            }

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
}
