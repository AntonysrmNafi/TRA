package com.blockveil.tracker.remover.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cleaned_links")
data class CleanedLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val original: String,
    val cleaned: String,
    val domain: String,
    val removedParamsCount: Int,
    /** Comma-separated tracker names (and "Short URL (resolved)" when applicable), for the detail screen. */
    val removedParamsNames: String,
    /** The "why this matters" plain-language sentence, or null if there was nothing to say. */
    val description: String?,
    val verdict: String, // stores Verdict.name, e.g. "SAFE" / "UNKNOWN"
    val timestampMillis: Long
)
