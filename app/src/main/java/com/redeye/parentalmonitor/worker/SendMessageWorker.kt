package com.redeye.parentalmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.redeye.parentalmonitor.data.MessageQueue
import com.redeye.parentalmonitor.data.PreferencesManager
import com.redeye.parentalmonitor.network.TelegramClient
import com.redeye.parentalmonitor.network.TelegramMessage
import kotlinx.coroutines.delay

class SendMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val messageQueue = MessageQueue(context)
    private val preferencesManager = PreferencesManager(context)

    override suspend fun doWork(): Result {
        android.util.Log.i("SendMessageWorker", "=== Starting message send worker ===")
        
        if (!preferencesManager.isConfigured()) {
            android.util.Log.e("SendMessageWorker", "Not configured, aborting")
            return Result.failure()
        }

        val queue = messageQueue.getQueue()
        android.util.Log.i("SendMessageWorker", "Queue size: ${queue.size}")
        
        if (queue.isEmpty()) {
            return Result.success()
        }

        var successCount = 0
        var failCount = 0

        for (queuedMessage in queue) {
            try {
                android.util.Log.d("SendMessageWorker", "Attempting to send queued message (retry: ${queuedMessage.retryCount})")
                val success = sendMessage(queuedMessage.message)
                if (success) {
                    android.util.Log.i("SendMessageWorker", "✓ Queued message sent successfully")
                    messageQueue.removeMessage(queuedMessage.id)
                    successCount++
                    delay(500) // Small delay between messages
                } else {
                    android.util.Log.w("SendMessageWorker", "✗ Failed to send queued message")
                    // Increment retry count
                    messageQueue.updateRetryCount(queuedMessage.id)
                    failCount++
                    
                    // Remove if too many retries
                    if (queuedMessage.retryCount >= 5) {
                        android.util.Log.w("SendMessageWorker", "Message exceeded retry limit, removing from queue")
                        messageQueue.removeMessage(queuedMessage.id)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SendMessageWorker", "Exception processing message", e)
                e.printStackTrace()
                failCount++
            }
        }

        android.util.Log.i("SendMessageWorker", "Worker complete - Success: $successCount, Failed: $failCount")
        
        return if (failCount == 0) {
            Result.success()
        } else if (successCount > 0) {
            Result.retry() // Some succeeded, retry for the rest
        } else {
            Result.retry() // All failed, retry later
        }
    }

    private suspend fun sendMessage(message: String): Boolean {
        return try {
            val botToken = preferencesManager.botToken
            val chatId = preferencesManager.chatId

            android.util.Log.d("SendMessageWorker", "Bot: ${botToken.take(20)}..., Chat: $chatId")

            val telegramMessage = TelegramMessage(
                chatId = chatId,
                text = message,
                parseMode = "HTML"
            )

            val url = "https://api.telegram.org/bot${botToken}/sendMessage"
            android.util.Log.d("SendMessageWorker", "URL: $url")
            val response = TelegramClient.api.sendMessage(url, telegramMessage)
            
            android.util.Log.d("SendMessageWorker", "Response code: ${response.code()}, successful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body()?.ok == true) {
                android.util.Log.i("SendMessageWorker", "✓ Telegram API returned success")
                true
            } else {
                android.util.Log.e("SendMessageWorker", "✗ Telegram API error: ${response.code()} - ${response.message()}")
                android.util.Log.e("SendMessageWorker", "Error: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("SendMessageWorker", "✗ Exception: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
}

