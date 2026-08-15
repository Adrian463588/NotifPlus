package com.notifplus.domain.repository

import androidx.paging.PagingData
import com.notifplus.domain.model.AutoDismissStatus
import com.notifplus.domain.model.HistoryQuery
import com.notifplus.domain.model.NotificationArchive
import com.notifplus.domain.model.NotificationCapture
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.NotificationThreadDetail
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.model.RemovalReason
import com.notifplus.domain.model.RemovalOrigin
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeHistory(query: HistoryQuery): Flow<PagingData<NotificationThreadSummary>>
    fun observeKnownPackages(): Flow<List<String>>
    suspend fun findSnapshot(snapshotId: String): NotificationSnapshot?
    suspend fun findLatestSnapshotByNotificationKey(notificationKey: String): NotificationSnapshot?
    suspend fun appendCapture(capture: NotificationCapture)
    suspend fun getThreadDetail(threadId: String): NotificationThreadDetail?
    suspend fun exportArchive(): NotificationArchive
    suspend fun importArchive(archive: NotificationArchive): Int
    suspend fun markRemoved(
        threadId: String,
        removedAt: Long,
        reason: RemovalReason,
        reasonCode: Int,
        origin: RemovalOrigin,
        dismissedByNotifPlus: Boolean,
    )
    suspend fun updateRanking(snapshotId: String, importance: Int?, rank: Int?)
    suspend fun updateAutoDismissStatus(threadId: String, status: AutoDismissStatus)
    suspend fun markRead(threadId: String, isRead: Boolean)
    suspend fun markFavorite(threadId: String, isFavorite: Boolean)
    suspend fun delete(threadId: String)
    suspend fun deleteExpired(before: Long)
    suspend fun deleteAll()
}
