package com.example.butlermanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class QrDataDao {
    @Query("SELECT * FROM qr_data WHERE name = :name")
    abstract suspend fun getQrDataByName(name: String): QrData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(qrData: QrData): Long

    @Query("SELECT * FROM qr_data")
    abstract suspend fun getAllQrData(): List<QrData>

    @Query("DELETE FROM qr_data WHERE name = :name")
    abstract suspend fun deleteQrDataByName(name: String): Int
}
