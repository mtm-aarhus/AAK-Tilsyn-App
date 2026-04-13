package com.aak.tilsynsapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TilsynDao {

    @Query("SELECT * FROM TilsynRows WHERE FakturaStatus = :status")
    suspend fun getByStatus(status: String): List<TilsynRow>

    @Query("SELECT * FROM TilsynRows")
    suspend fun getAll(): List<TilsynRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<TilsynRow>)

    @Query("DELETE FROM TilsynRows WHERE FakturaStatus = :status")
    suspend fun clearStatus(status: String)

    @Query("DELETE FROM TilsynRows")
    suspend fun clearAll()

    @Update
    suspend fun updateRow(row: TilsynRow)

}
