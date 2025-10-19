package com.redeye.parentalmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.*
import com.redeye.parentalmonitor.data.MessageQueue
import com.redeye.parentalmonitor.worker.SendMessageWorker
import java.util.concurrent.TimeUnit

class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (isNetworkAvailable(context)) {
            val messageQueue = MessageQueue(context)
            if (messageQueue.hasMessages()) {
                scheduleMessageSend(context)
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }

    companion object {
        fun scheduleMessageSend(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val sendRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "send_queued_messages",
                    ExistingWorkPolicy.REPLACE,
                    sendRequest
                )
        }
    }
}

