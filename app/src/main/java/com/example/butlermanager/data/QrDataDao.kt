package com.example.butlermanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QrDataDao {
    @Query("SELECT * FROM qr_data WHERE name = :name")
    suspend fun getQrDataByName(name: String): QrData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(qrData: QrData)

    @Query("SELECT * FROM qr_data")
    suspend fun getAllQrData(): List<QrData>

    @Query("DELETE FROM qr_data WHERE name = :name")
    suspend fun deleteQrDataByName(name: String)
}
