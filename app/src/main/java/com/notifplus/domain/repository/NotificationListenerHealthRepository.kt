package com.notifplus.domain.repository

import com.notifplus.domain.model.NotificationListenerHealth
import kotlinx.coroutines.flow.Flow

interface NotificationListenerHealthRepository {
    fun observeHealth(): Flow<NotificationListenerHealth>

    fun current(): NotificationListenerHealth

    suspend fun markConnected(at: Long = System.currentTimeMillis())

    suspend fun markDisconnected(at: Long = System.currentTimeMillis())

    suspend fun markReconnecting(at: Long = System.currentTimeMillis())

    suspend fun markAccessRequired(at: Long = System.currentTimeMillis())

    suspend fun markPosted(at: Long = System.currentTimeMillis())

    suspend fun markPersisted(at: Long = System.currentTimeMillis())

    suspend fun markFailure()

    fun setQueueDepth(depth: Int)
}
