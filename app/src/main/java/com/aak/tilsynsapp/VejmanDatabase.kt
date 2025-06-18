package com.aak.tilsynsapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VejmanKassenRow::class], version = 2, exportSchema = false)
abstract class VejmanDatabase : RoomDatabase() {
    abstract fun vejmanDao(): VejmanDao

    companion object {
        @Volatile
        private var INSTANCE: VejmanDatabase? = null

        fun getDatabase(context: Context): VejmanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VejmanDatabase::class.java,
                    "vejman_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
