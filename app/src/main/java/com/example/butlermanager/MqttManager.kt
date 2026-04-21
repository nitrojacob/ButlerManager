package com.example.butlermanager

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.butlermanager.data.QrData
import com.example.butlermanager.data.TimeEntryDatabase
import com.example.butlermanager.data.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MqttManager(private val context: Context) : DeviceManager {
    private var mqttClient: MqttAndroidClient? = null
    private val timeEntryDao = TimeEntryDatabase.getDatabase(context).timeEntryDao()
    private var deviceName: String? = null
    private var mac: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    var brokerUrl: String = "tcp://iothub.local:1883"
        private set

    fun setBrokerUrl(hostname: String) {
        brokerUrl = "tcp://$hostname:1883"
        Log.d(TAG, "Broker URL set to: $brokerUrl")
    }

    override var ssid: String? = ""
    override var password: String? = ""
    override var timeServer: String? = "iothub.local"
    override var mqttBroker: String? = "iothub.local"
    override var otaHost: String? = "iothub.local"
    override var timeSlots by mutableStateOf<List<TimeSlot>>(emptyList())
    override var initialTimeSlots by mutableStateOf<List<TimeSlot>>(emptyList())
    
    override var isConnected by mutableStateOf(false)
        private set

    private fun getName(): String {
        val mac = mac ?: ""
        return "BUTLER_${mac}/"
    }

    override suspend fun connect(qrData: QrData) {
        deviceName = qrData.name
        mac = qrData.password
        val clientId = MqttClient.generateClientId()
        val client = MqttAndroidClient(context.applicationContext, brokerUrl, clientId)
        mqttClient = client

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false
        }

        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                isConnected = true
            }

            override fun connectionLost(cause: Throwable?) {
                isConnected = false
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                // Global message handler could be implemented here
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        return suspendCancellableCoroutine { continuation ->
            try {
                client.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "MQTT Connected to $brokerUrl")
                        isConnected = true
                        continuation.resume(Unit)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "MQTT Connection failed", exception)
                        isConnected = false
                        continuation.resumeWithException(exception ?: Exception("Failed to connect to MQTT broker"))
                    }
                })
            } catch (e: MqttException) {
                isConnected = false
                continuation.resumeWithException(e)
            }
        }
    }

    override fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: MqttException) {
            Log.e(TAG, "Error disconnecting MQTT", e)
        }
        mqttClient = null
        isConnected = false
    }

    // Mocking provision as it doesn't apply to pure MQTT mode in the same way, 
    // but kept for API compatibility.
    override suspend fun provision() {
        Log.d(TAG, "Provisioning over MQTT is not implemented/needed")
    }

    override suspend fun readCronData() {
        val dn = deviceName ?: throw IllegalStateException("Device name not set")
        val topic = getName() + "nvCron/rd"
        val responseTopic = topic + "Data"
        
        val requestData = byteArrayOf(0, 24, 0, 0)
        try {
            val response = sendAndReceive(topic, responseTopic, requestData)
            
            scope.launch {
                try {
                    val parsedTimeSlots = parseCronData(dn, response)
                    
                    timeSlots = parsedTimeSlots
                    timeEntryDao.updateTimeSlotsForConfiguration(dn, parsedTimeSlots)
                } catch (e: Exception) {
                    Log.e(TAG, "readCronData: Failed to parse or update time slots in database", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readCronData: Error during sendAndReceive on topic $topic", e)
            throw e
        }
    }

    private suspend fun readPLogLine(): String? {
        val topic = getName() + "plogRd"
        val responseTopic = topic + "Data"
        return try {
            val response = sendAndReceive(topic, responseTopic, byteArrayOf(0, 0, 0, 0))
            response.toString(Charsets.UTF_8).trimEnd('\u0000')
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PLog line", e)
            null
        }
    }

    override suspend fun readPLog(): String {
        val lines = mutableListOf<String>()
        var firstLine: String? = null

        while (true) {
            val line = readPLogLine() ?: break
            if (line.isEmpty()) break

            if (firstLine == null) {
                firstLine = line
            } else if (line == firstLine) {
                break
            }
            lines.add(line)
            
            if (lines.size > 1000) break 
        }

        if (lines.isEmpty()) return ""

        val dashIndex = lines.indexOf("-")
        if (dashIndex == -1) {
            return lines.joinToString("\n")
        }

        val oldestPart = lines.subList(dashIndex + 1, lines.size)
        val newestPart = lines.subList(0, dashIndex)
        val reorderedLines = oldestPart + newestPart
        
        return reorderedLines.filter { it != "-" && it.isNotBlank() }.joinToString("\n")
    }

    override suspend fun readVersion(): String {
        val topic = getName() + "version"
        val responseTopic = topic + "Data"
        return try {
            val response = sendAndReceive(topic, responseTopic, byteArrayOf(0, 0, 0, 0))
            response.toString(Charsets.UTF_8).trimEnd('\u0000')
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read version", e)
            "Unknown"
        }
    }

    override suspend fun readSysStat(): String {
        val topic = getName() + "sysStat"
        val responseTopic = topic + "Data"
        return try {
            val response = sendAndReceive(topic, responseTopic, byteArrayOf(0, 0, 0, 0))
            response.toString(Charsets.UTF_8).trimEnd('\u0000')
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read sysStat", e)
            "Unknown"
        }
    }

    override suspend fun writeCronData() {
        val topic = getName() + "nvCron/wr"
        val cronData = packCronData(timeSlots)
        publish(topic, cronData)
    }

    override suspend fun writeTimeData() {
        val topic = getName() + "time/set"
        val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH)
        val currentTime = sdf.format(Date())
        publish(topic, currentTime.toByteArray(Charsets.US_ASCII))
    }

    override suspend fun writeAdvancedConfigs() {
        val topic = getName() + "bcfgWr"
        val timeServerBytes = (timeServer ?: "").toByteArray(Charsets.UTF_8) + 0.toByte()
        val mqttBrokerBytes = (mqttBroker ?: "").toByteArray(Charsets.UTF_8) + 0.toByte()
        val otaHostBytes = (otaHost ?: "").toByteArray(Charsets.UTF_8) + 0.toByte()
        val data = timeServerBytes + mqttBrokerBytes + otaHostBytes
        publish(topic, data)
    }

    private suspend fun publish(topic: String, data: ByteArray) {
        val client = mqttClient ?: throw IllegalStateException("MQTT client not connected")
        return suspendCancellableCoroutine { continuation ->
            try {
                client.publish(topic, data, 1, false, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        continuation.resume(Unit)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        continuation.resumeWithException(exception ?: Exception("Failed to publish to $topic"))
                    }
                })
            } catch (e: MqttException) {
                continuation.resumeWithException(e)
            }
        }
    }

    private suspend fun sendAndReceive(topic: String, responseTopic: String, data: ByteArray): ByteArray {
        val client = mqttClient ?: throw IllegalStateException("MQTT client not connected")
        
        return suspendCancellableCoroutine { continuation ->
            val tempCallback = object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    isConnected = true
                }
                override fun connectionLost(cause: Throwable?) {
                    isConnected = false
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}

                override fun messageArrived(receivedTopic: String?, message: MqttMessage?) {
                    if (receivedTopic == responseTopic) {
                        continuation.resume(message?.payload ?: byteArrayOf())
                    }
                }
            }
            
            client.setCallback(tempCallback)
            
            try {
                client.subscribe(responseTopic, 1, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        try {
                            client.publish(topic, data, 1, false)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        continuation.resumeWithException(exception ?: Exception("Failed to subscribe to $responseTopic"))
                    }
                })
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
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
        val chunkSize = 4
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
                timeSlots.add(timeSlot)
            }
        }
        return timeSlots
    }

    companion object {
        private const val TAG = "MqttManager"
    }
}
