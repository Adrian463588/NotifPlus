package com.notifplus.domain.usecase

import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.notifplus.domain.model.AutoDismissRule
import com.notifplus.domain.model.AutoDismissStatus
import com.notifplus.domain.model.CaptureOrigin
import com.notifplus.domain.model.HistoryQuery
import com.notifplus.domain.model.NotificationArchive
import com.notifplus.domain.model.NotificationCapture
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.NotificationThreadDetail
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.model.PayloadAvailability
import com.notifplus.domain.model.RemovalOrigin
import com.notifplus.domain.model.RemovalReason
import com.notifplus.domain.repository.AutoDismissRepository
import com.notifplus.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CaptureNotificationUseCaseTest {
    @Test
    fun `database failure prevents auto dismiss lookup`() = runTest {
        val autoDismiss = RecordingAutoDismissRepository()
        val useCase = CaptureNotificationUseCase(FailingNotificationRepository(), autoDismiss)

        val failure = runCatching {
            useCase(NotificationCapture(sampleSnapshot(), emptyList(), emptyList()))
        }.exceptionOrNull()

        assertThat(failure).isNotNull()
        assertThat(autoDismiss.lookupCount).isEqualTo(0)
    }
}

private class RecordingAutoDismissRepository : AutoDismissRepository {
    var lookupCount = 0

    override fun observeRules(): Flow<List<AutoDismissRule>> = emptyFlow()
    override suspend fun isEnabledFor(packageName: String): Boolean {
        lookupCount++
        return true
    }
    override suspend fun setEnabled(packageName: String, enabled: Boolean) = Unit
}

private class FailingNotificationRepository : NotificationRepository {
    override fun observeHistory(query: HistoryQuery): Flow<PagingData<NotificationThreadSummary>> = emptyFlow()
    override fun observeKnownPackages(): Flow<List<String>> = emptyFlow()
    override suspend fun findSnapshot(snapshotId: String): NotificationSnapshot? = null
    override suspend fun findLatestSnapshotByNotificationKey(notificationKey: String): NotificationSnapshot? = null
    override suspend fun appendCapture(capture: NotificationCapture): Unit = error("database write failed")
    override suspend fun getThreadDetail(threadId: String): NotificationThreadDetail? = null
    override suspend fun exportArchive(): NotificationArchive = NotificationArchive(threads = emptyList())
    override suspend fun importArchive(archive: NotificationArchive): Int = 0
    override suspend fun markRemoved(
        threadId: String,
        removedAt: Long,
        reason: RemovalReason,
        reasonCode: Int,
        origin: RemovalOrigin,
        dismissedByNotifPlus: Boolean,
    ) = Unit
    override suspend fun updateRanking(snapshotId: String, importance: Int?, rank: Int?) = Unit
    override suspend fun updateAutoDismissStatus(threadId: String, status: AutoDismissStatus) = Unit
    override suspend fun markRead(threadId: String, isRead: Boolean) = Unit
    override suspend fun markFavorite(threadId: String, isFavorite: Boolean) = Unit
    override suspend fun delete(threadId: String) = Unit
    override suspend fun deleteExpired(before: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private fun sampleSnapshot() = NotificationSnapshot(
    snapshotId = "snapshot-1",
    threadId = "thread-1",
    notificationKey = "key-1",
    userProfileKey = "user-0",
    packageName = "com.example.source",
    appLabel = "Source",
    title = "Title",
    titleBig = "",
    text = "Sensitive body",
    bigText = "",
    subText = "",
    infoText = "",
    summaryText = "",
    conversationTitle = "",
    tickerText = "",
    template = "",
    textLines = emptyList(),
    remoteInputHistory = emptyList(),
    structuredExtrasJson = "{}",
    payloadAvailability = PayloadAvailability.AVAILABLE,
    previewText = "Sensitive body",
    category = "msg",
    channelId = "channel",
    channelName = "Channel",
    postedAt = 1L,
    capturedAt = 1L,
    isOngoing = false,
    isClearable = true,
    isGroupSummary = false,
    isConversation = true,
    importance = null,
    rank = null,
    captureOrigin = CaptureOrigin.LIVE_POST,
)
