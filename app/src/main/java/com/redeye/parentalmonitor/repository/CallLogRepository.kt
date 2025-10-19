package com.redeye.parentalmonitor.repository

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import com.redeye.parentalmonitor.data.models.CallData

class CallLogRepository(private val context: Context) {

    fun getNewCalls(afterTimestamp: Long): List<CallData> {
        val callList = mutableListOf<CallData>()
        
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                "${CallLog.Calls.DATE} > ?",
                arrayOf(afterTimestamp.toString()),
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val numberIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val dateIndex = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)

                while (it.moveToNext()) {
                    try {
                        val number = it.getString(numberIndex) ?: "Unknown"
                        val name = it.getString(nameIndex)
                        
                        val call = CallData(
                            number = number,
                            name = name ?: getContactName(number),
                            date = it.getLong(dateIndex),
                            duration = it.getInt(durationIndex),
                            type = it.getInt(typeIndex)
                        )
                        callList.add(call)
                    } catch (e: Exception) {
                        // Skip corrupted call log
                        android.util.Log.e("CallLogRepository", "Error reading call", e)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return callList
    }

    fun getRecentCalls(limit: Int = 20): List<CallData> {
        val callList = mutableListOf<CallData>()
        
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )

            cursor?.use {
                val numberIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val dateIndex = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)

                while (it.moveToNext()) {
                    try {
                        val number = it.getString(numberIndex) ?: "Unknown"
                        val name = it.getString(nameIndex)
                        
                        val call = CallData(
                            number = number,
                            name = name ?: getContactName(number),
                            date = it.getLong(dateIndex),
                            duration = it.getInt(durationIndex),
                            type = it.getInt(typeIndex)
                        )
                        callList.add(call)
                    } catch (e: Exception) {
                        // Skip corrupted call log
                        android.util.Log.e("CallLogRepository", "Error reading call", e)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return callList
    }

    private fun getContactName(phoneNumber: String): String? {
        try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(phoneNumber)
                .build()

            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getAllCalls(limit: Int = 200): List<CallData> {
        val callList = mutableListOf<CallData>()
        
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )

            cursor?.use {
                val numberIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val dateIndex = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)

                while (it.moveToNext()) {
                    try {
                        val number = it.getString(numberIndex) ?: "Unknown"
                        val name = it.getString(nameIndex)
                        
                        val call = CallData(
                            number = number,
                            name = name ?: getContactName(number),
                            date = it.getLong(dateIndex),
                            duration = it.getInt(durationIndex),
                            type = it.getInt(typeIndex)
                        )
                        callList.add(call)
                    } catch (e: Exception) {
                        // Skip corrupted call log
                        android.util.Log.e("CallLogRepository", "Error reading call", e)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return callList
    }
}

