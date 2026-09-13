package com.blockveil.tracker.remover.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CleanedLinkEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cleanedLinkDao(): CleanedLinkDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tracker_remover.db"
                )
                    // Schema changed (added removedParamsNames/description columns) and
                    // there's no shipped release depending on the old schema yet, so a
                    // destructive fallback is simpler than a hand-written migration.
                    // This clears any history saved under the previous version.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
