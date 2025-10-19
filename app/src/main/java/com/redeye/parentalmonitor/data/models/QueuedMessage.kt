package com.redeye.parentalmonitor.data.models

data class QueuedMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

