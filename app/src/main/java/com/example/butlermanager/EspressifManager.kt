package com.example.butlermanager

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.ProvisionListener
import com.espressif.provisioning.listeners.ResponseListener
import com.example.butlermanager.data.QrData
import com.example.butlermanager.data.TimeEntryDatabase
import com.example.butlermanager.data.TimeSlot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class EspressifManager(context: Context) {
    private val provisionManager: ESPProvisionManager = ESPProvisionManager.getInstance(context.applicationContext)
    private var espDevice: ESPDevice? = null
    private val timeEntryDao = TimeEntryDatabase.getDatabase(context).timeEntryDao()
    private var deviceName: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var ssid: String ?= ""
    var password: String?= ""
    var timeServer: String? = "iothub.local"
    var mqttBroker: String? = "iothub.local"
    var otaHost: String? = "iothub.local"
    var timeSlots by mutableStateOf<List<TimeSlot>>(emptyList())
    var initialTimeSlots by mutableStateOf<List<TimeSlot>>(emptyList())


    suspend fun connect(qrData: QrData) {
        val transport = qrData.transport ?: "softap" // Default to softap
        val transportType = if (transport.equals("ble", ignoreCase = true)) {
            ESPConstants.TransportType.TRANSPORT_BLE
        } else {
            ESPConstants.TransportType.TRANSPORT_SOFTAP
        }

        val security = qrData.security ?: "0" // Default to security 0
        val securityType = if (security == "1") {
            ESPConstants.SecurityType.SECURITY_1
        } else {
            ESPConstants.SecurityType.SECURITY_0
        }

        if (securityType == ESPConstants.SecurityType.SECURITY_1 && qrData.pop == null) {
            throw IllegalArgumentException("Proof of possession is required for Security 1")
        }

        if (transportType == ESPConstants.TransportType.TRANSPORT_BLE && qrData.name == null) {
            throw IllegalArgumentException("Device name is required for BLE transport")
        }

        val device = provisionManager.createESPDevice(transportType, securityType)
        espDevice = device
        deviceName = qrData.name

        device.proofOfPossession = qrData.pop ?: ""
        device.deviceName = qrData.name ?: ""

        return suspendCancellableCoroutine { continuation ->
            val eventBus = EventBus.getDefault()

            val subscriber = object {
                @Subscribe(threadMode = ThreadMode.MAIN)
                fun onDeviceConnectionEvent(event: DeviceConnectionEvent) {
                    // Once we get an event, unregister the listener to avoid leaks and multiple callbacks.
                    if (eventBus.isRegistered(this)) {
                        eventBus.unregister(this)
                    }

                    if (continuation.isActive) {
                        when (event.eventType) {
                            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                                Log.d(TAG, "Device connected")
                                continuation.resume(Unit)
                            }
                            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> {
                                Log.e(TAG, "Device connection failed")
                                continuation.resumeWithException(Exception("Failed to connect to device"))
                            }
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                // If the coroutine is cancelled, we should also unregister the listener.
                if (eventBus.isRegistered(subscriber)) {
                    eventBus.unregister(subscriber)
                }
            }

            eventBus.register(subscriber)

            if (transportType == ESPConstants.TransportType.TRANSPORT_SOFTAP) {
                // For SoftAP, we need to connect to the device's Wi-Fi network first.
                device.connectWiFiDevice(qrData.name, qrData.password)
            } else {
                // BLE transport requires scanning first, which is not implemented.
                if (eventBus.isRegistered(subscriber)) {
                    eventBus.unregister(subscriber)
                }
                if (continuation.isActive) {
                    continuation.resumeWithException(NotImplementedError("Only SoftAP is supported at the moment."))
                }
            }
        }
    }

    fun disconnect() {
        val device = espDevice ?: throw IllegalStateException("Device not connected")
        device.disconnectDevice()
    }


    suspend fun provision() {
        val device = espDevice ?: throw IllegalStateException("Device not connected")
        val ssid = ssid ?: ""
        val password = password ?: ""

        return suspendCancellableCoroutine { continuation ->
            device.provision(ssid, password, object : ProvisionListener {
                override fun createSessionFailed(e: Exception?) {
                    Log.e(TAG, "Create session failed", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e!!)
                    }
                }

                override fun wifiConfigSent() {
                    Log.d(TAG, "Wifi config sent")
                }

                override fun wifiConfigFailed(e: Exception?) {
                    Log.e(TAG, "Wifi config failed", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e!!)
                    }
                }

                override fun wifiConfigApplied() {
                    Log.d(TAG, "Wifi config applied")
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun wifiConfigApplyFailed(e: Exception?) {
                    Log.e(TAG, "Wifi config apply failed", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e!!)
                    }
                }

                override fun provisioningFailedFromDevice(failureReason: ESPConstants.ProvisionFailureReason?) {
                    Log.e(TAG, "Provisioning failed from device with reason: $failureReason")
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("Provisioning failed from device with reason: $failureReason"))
                    }
                }

                override fun deviceProvisioningSuccess() {
                    Log.d(TAG, "Provisioning successful")
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onProvisioningFailed(e: Exception?) {
                    Log.e(TAG, "Provisioning failed", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e!!)
                    }
                }
            })
        }
    }

    suspend fun readCronData() {
        val device = espDevice ?: throw IllegalStateException("Device not connected")
        val dn = deviceName ?: throw IllegalStateException("Device name not set")

        return suspendCancellableCoroutine { continuation ->
            device.sendDataToCustomEndPoint("cronRd", byteArrayOf(0, 24, 0, 0), object : ResponseListener {
                override fun onSuccess(response: ByteArray?) {
                    Log.d(TAG, "Custom data received: ${response?.contentToString()}")
                    scope.launch {
                        try {
                            if (continuation.isActive) {
                                response?.let {
                                    val parsedTimeSlots = parseCronData(dn, it)
                                    timeSlots = parsedTimeSlots
                                    timeEntryDao.updateTimeSlotsForConfiguration(dn, parsedTimeSlots)
                                }
                                continuation.resume(Unit)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update time slots", e)
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                }

                override fun onFailure(e: Exception) {
                    Log.e(TAG, "Failed to read custom data", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    suspend fun writeCronData() {
        val device = espDevice ?: throw IllegalStateException("Device not connected")
        val cronData = packCronData(timeSlots)

        return suspendCancellableCoroutine { continuation ->
            device.sendDataToCustomEndPoint("cronWr", cronData, object : ResponseListener {
                override fun onSuccess(response: ByteArray?) {
                    Log.d(TAG, "Successfully wrote cron data")
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onFailure(e: Exception) {
                    Log.e(TAG, "Failed to write cron data", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    suspend fun writeTimeData() {
        /* Writes current time */
        val device = espDevice ?: throw IllegalStateException("Device not connected")

        val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH)
        val currentTime = sdf.format(Date())

        val timeData = currentTime.toByteArray(Charsets.US_ASCII)

        return suspendCancellableCoroutine { continuation ->
            device.sendDataToCustomEndPoint("timeWr", timeData, object : ResponseListener {
                override fun onSuccess(response: ByteArray?) {
                    Log.d(TAG, "Successfully wrote time data")
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onFailure(e: Exception) {
                    Log.e(TAG, "Failed to write time data", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    suspend fun writeAdvancedConfigs() {
        val device = espDevice ?: throw IllegalStateException("Device not connected")

        val timeServerBytes = (timeServer ?: "").toByteArray(Charsets.UTF_8) + 0.toByte()
        val mqttBrokerBytes = (mqttBroker ?: "").toByteArray(Charsets.UTF_8) + 0.toByte()
        val otaHostBytes = (otaHost ?: "").toByteArray(Charsets.UTF_8) + 0.toByte()

        val data = timeServerBytes + mqttBrokerBytes + otaHostBytes

        return suspendCancellableCoroutine { continuation ->
            device.sendDataToCustomEndPoint("bcfgWr", data, object : ResponseListener {
                override fun onSuccess(response: ByteArray?) {
                    Log.d(TAG, "Successfully wrote advanced configs")
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onFailure(e: Exception) {
                    Log.e(TAG, "Failed to write advanced configs", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    private fun packCronData(timeSlots: List<TimeSlot>): ByteArray {
        val byteList = mutableListOf<Byte>()
        for (timeSlot in timeSlots) {
            byteList.add(timeSlot.minute.toByte())
            byteList.add(timeSlot.hour.toByte())
            byteList.add(timeSlot.onOff.toByte())
            byteList.add(timeSlot.channel.toByte())
        }
        return byteList.toByteArray()
    }

    private fun parseCronData(deviceName: String, data: ByteArray): List<TimeSlot> {
        val timeSlots = mutableListOf<TimeSlot>()
        val chunkSize = 4 // hour, minute, onOff, channel
        data.asList().chunked(chunkSize).forEachIndexed { index, chunk ->
            if (chunk.size == chunkSize) {
                val timeSlot = TimeSlot(
                    configurationName = deviceName,
                    rowIndex = index,
                    minute = chunk[0].toUByte().toInt(),
                    hour = chunk[1].toUByte().toInt(),
                    onOff = chunk[2].toUByte().toInt(),
                    channel = chunk[3].toUByte().toInt(),
                )
                Log.d(TAG, "Parsed TimeSlot: $timeSlot")
                timeSlots.add(timeSlot)
            }
        }
        return timeSlots
    }


    companion object {
        private const val TAG = "EspressifManager"
    }
}
