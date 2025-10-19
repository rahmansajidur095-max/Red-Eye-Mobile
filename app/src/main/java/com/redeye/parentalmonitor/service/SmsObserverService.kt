package com.redeye.parentalmonitor.service

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.redeye.parentalmonitor.data.PreferencesManager
import com.redeye.parentalmonitor.repository.SmsRepository
import kotlinx.coroutines.*

class SmsObserverService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var smsRepository: SmsRepository
    private var smsObserver: ContentObserver? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        smsRepository = SmsRepository(this)
        startObserving()
    }

    private fun startObserving() {
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                // When SMS database changes, trigger monitoring service to check
                serviceScope.launch {
                    delay(1000) // Small delay to ensure SMS is fully written
                    // The monitoring service will pick up new messages in its regular check
                }
            }
        }

        contentResolver.registerContentObserver(
            Uri.parse("content://sms"),
            true,
            smsObserver!!
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        smsObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        serviceScope.cancel()
    }
}

