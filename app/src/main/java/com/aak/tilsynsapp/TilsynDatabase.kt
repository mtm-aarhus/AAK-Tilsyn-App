package com.aak.tilsynsapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TilsynRow::class], version = 4, exportSchema = false)
abstract class TilsynDatabase : RoomDatabase() {
    abstract fun tilsynDao(): TilsynDao

    companion object {
        @Volatile
        private var INSTANCE: TilsynDatabase? = null

        @Suppress("unused")
        fun getDatabase(context: Context): TilsynDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TilsynDatabase::class.java,
                    "tilsyn_database"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
