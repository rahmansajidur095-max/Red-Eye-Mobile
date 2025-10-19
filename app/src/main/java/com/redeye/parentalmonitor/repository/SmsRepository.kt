package com.redeye.parentalmonitor.repository

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.redeye.parentalmonitor.data.models.SmsData

class SmsRepository(private val context: Context) {

    fun getNewSms(afterId: Long): List<SmsData> {
        val smsList = mutableListOf<SmsData>()
        
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                "${Telephony.Sms._ID} > ?",
                arrayOf(afterId.toString()),
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    try {
                        val sms = SmsData(
                            id = it.getLong(idIndex),
                            address = it.getString(addressIndex) ?: "Unknown",
                            body = it.getString(bodyIndex) ?: "(bo'sh xabar)",
                            date = it.getLong(dateIndex),
                            type = it.getInt(typeIndex),
                            read = it.getInt(readIndex) == 1
                        )
                        smsList.add(sms)
                    } catch (e: Exception) {
                        // Skip corrupted SMS
                        android.util.Log.e("SmsRepository", "Error reading SMS", e)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return smsList
    }

    fun getRecentSms(limit: Int = 20): List<SmsData> {
        val smsList = mutableListOf<SmsData>()
        
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    try {
                        val sms = SmsData(
                            id = it.getLong(idIndex),
                            address = it.getString(addressIndex) ?: "Unknown",
                            body = it.getString(bodyIndex) ?: "(bo'sh xabar)",
                            date = it.getLong(dateIndex),
                            type = it.getInt(typeIndex),
                            read = it.getInt(readIndex) == 1
                        )
                        smsList.add(sms)
                    } catch (e: Exception) {
                        // Skip corrupted SMS
                        android.util.Log.e("SmsRepository", "Error reading SMS", e)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return smsList
    }

    fun getAllSms(limit: Int = 200): List<SmsData> {
        val smsList = mutableListOf<SmsData>()
        
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    try {
                        val sms = SmsData(
                            id = it.getLong(idIndex),
                            address = it.getString(addressIndex) ?: "Unknown",
                            body = it.getString(bodyIndex) ?: "(bo'sh xabar)",
                            date = it.getLong(dateIndex),
                            type = it.getInt(typeIndex),
                            read = it.getInt(readIndex) == 1
                        )
                        smsList.add(sms)
                    } catch (e: Exception) {
                        // Skip corrupted SMS
                        android.util.Log.e("SmsRepository", "Error reading SMS", e)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return smsList
    }
}

