package com.aak.tilsynsapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface VejmanDao {

    @Query("SELECT * FROM VejmanKassen WHERE FakturaStatus = :status")
    suspend fun getByStatus(status: String): List<VejmanKassenRow>

    @Query("SELECT * FROM VejmanKassen")
    suspend fun getAll(): List<VejmanKassenRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<VejmanKassenRow>)

    @Query("DELETE FROM VejmanKassen WHERE FakturaStatus = :status")
    suspend fun clearStatus(status: String)

    @Query("DELETE FROM VejmanKassen")
    suspend fun clearAll()

    @Update
    suspend fun updateRow(row: VejmanKassenRow)

}
