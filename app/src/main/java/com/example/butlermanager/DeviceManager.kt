package com.example.butlermanager

import com.example.butlermanager.data.QrData
import com.example.butlermanager.data.TimeSlot

interface DeviceManager {
    var timeSlots: List<TimeSlot>
    var initialTimeSlots: List<TimeSlot>
    var ssid: String?
    var password: String?
    var timeServer: String?
    var mqttBroker: String?
    var otaHost: String?
    val isConnected: Boolean
    
    suspend fun connect(qrData: QrData)
    fun disconnect()
    suspend fun provision()
    suspend fun readCronData()
    suspend fun readPLog(): String
    suspend fun readVersion(): String
    suspend fun readSysStat(): String
    suspend fun writeCronData()
    suspend fun writeTimeData()
    suspend fun writeAdvancedConfigs()
}
