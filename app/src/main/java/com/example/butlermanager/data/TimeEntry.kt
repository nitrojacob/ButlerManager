package com.example.butlermanager.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "time_entry_configurations")
data class TimeEntryConfiguration(
    @PrimaryKey val name: String
)

@Entity(tableName = "time_slots")
data class TimeSlot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val configurationName: String,
    val rowIndex: Int,
    val hour: Int,
    val minute: Int,
    val onOff: Int,
    val channel: Int
)

data class ConfigurationWithTimeSlots(
    @Embedded val configuration: TimeEntryConfiguration,
    @Relation(
        parentColumn = "name",
        entityColumn = "configurationName"
    )
    val timeSlots: List<TimeSlot>
)
