package com.redeye.parentalmonitor.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        android.util.Log.i("AdminReceiver", "Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        android.util.Log.w("AdminReceiver", "Device Admin disabled!")
        Toast.makeText(context, "⚠️ Himoya o'chirildi!", Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        android.util.Log.w("AdminReceiver", "Attempting to disable Device Admin!")
        return "⚠️ Diqqat! Bu ilovani o'chirishdan oldin Device Admin'ni o'chirish kerak!"
    }
}

