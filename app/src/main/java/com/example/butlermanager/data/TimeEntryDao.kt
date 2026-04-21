package com.example.butlermanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class TimeEntryDao {

    @Transaction
    @Query("SELECT * FROM time_entry_configurations WHERE name = :name")
    abstract suspend fun getConfigurationWithTimeSlots(name: String): ConfigurationWithTimeSlots?

    @Query("SELECT * FROM time_entry_configurations")
    abstract suspend fun getAllConfigurations(): List<TimeEntryConfiguration>

    @Query("SELECT * FROM time_slots WHERE configurationName = :configurationName")
    abstract suspend fun getTimeSlotsForConfiguration(configurationName: String): List<TimeSlot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertConfiguration(configuration: TimeEntryConfiguration): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTimeSlot(timeSlot: TimeSlot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTimeSlots(timeSlots: List<TimeSlot>): List<Long>

    @Query("DELETE FROM time_slots WHERE configurationName = :configurationName")
    abstract suspend fun deleteTimeSlotsForConfiguration(configurationName: String): Int

    @Transaction
    open suspend fun updateTimeSlotsForConfiguration(configurationName: String, timeSlots: List<TimeSlot>): Int {
        insertConfiguration(TimeEntryConfiguration(configurationName))
        deleteTimeSlotsForConfiguration(configurationName)
        insertTimeSlots(timeSlots)
        return 1
    }

    @Transaction
    open suspend fun renameConfiguration(oldName: String, newName: String): Int {
        val timeSlots = getTimeSlotsForConfiguration(oldName)
        deleteTimeSlotsForConfiguration(oldName)
        timeSlots.forEach { insertTimeSlot(it.copy(configurationName = newName)) }
        deleteConfig(oldName)
        insertConfiguration(TimeEntryConfiguration(newName))
        return 1
    }

    @Query("DELETE FROM time_entry_configurations WHERE name = :name")
    abstract suspend fun deleteConfig(name: String): Int

    @Transaction
    open suspend fun deleteConfigurationAndSlots(name: String): Int {
        deleteTimeSlotsForConfiguration(name)
        deleteConfig(name)
        return 1
    }
}
