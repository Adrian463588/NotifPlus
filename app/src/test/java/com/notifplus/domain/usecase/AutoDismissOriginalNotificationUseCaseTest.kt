package com.notifplus.domain.usecase

import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.notifplus.domain.model.AutoDismissStatus
import com.notifplus.domain.model.CaptureOrigin
import com.notifplus.domain.model.HistoryQuery
import com.notifplus.domain.model.NotificationArchive
import com.notifplus.domain.model.NotificationCapture
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.NotificationThreadDetail
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.model.RemovalOrigin
import com.notifplus.domain.model.RemovalReason
import com.notifplus.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AutoDismissOriginalNotificationUseCaseTest {
    @Test
    fun `cancel happens only for clearable non ongoing notification`() = runTest {
        val repository = FakeNotificationRepository()
        val port = RecordingDismissalPort()
        val snapshot = sampleSnapshot(isClearable = true, isOngoing = false)

        val result = AutoDismissOriginalNotificationUseCase(repository)(snapshot, port)

        assertThat(result).isTrue()
        assertThat(port.cancelledKeys).containsExactly(snapshot.notificationKey)
        assertThat(repository.statuses[snapshot.threadId]).isEqualTo(AutoDismissStatus.REQUESTED)
    }

    @Test
    fun `ongoing notification is never cancelled`() = runTest {
        val repository = FakeNotificationRepository()
        val port = RecordingDismissalPort()
        val snapshot = sampleSnapshot(isClearable = true, isOngoing = true)

        val result = AutoDismissOriginalNotificationUseCase(repository)(snapshot, port)

        assertThat(result).isFalse()
        assertThat(port.cancelledKeys).isEmpty()
        assertThat(repository.statuses[snapshot.threadId]).isEqualTo(AutoDismissStatus.SKIPPED_NOT_CLEARABLE)
    }

    @Test
    fun `failed system cancellation is recorded without throwing`() = runTest {
        val repository = FakeNotificationRepository()
        val port = RecordingDismissalPort(shouldFail = true)

        val result = AutoDismissOriginalNotificationUseCase(repository)(sampleSnapshot(true, false), port)

        assertThat(result).isFalse()
        assertThat(repository.statuses.values).containsExactly(AutoDismissStatus.FAILED)
    }

    @Test
    fun `dismissal intent is persisted before system cancellation`() = runTest {
        val repository = FakeNotificationRepository()
        val snapshot = sampleSnapshot(true, false)
        val port = RecordingDismissalPort(onCancel = {
            assertThat(repository.statuses[snapshot.threadId]).isEqualTo(AutoDismissStatus.REQUESTED)
        })

        AutoDismissOriginalNotificationUseCase(repository)(snapshot, port)

        assertThat(port.cancelledKeys).containsExactly(snapshot.notificationKey)
    }
}

private class RecordingDismissalPort(
    private val shouldFail: Boolean = false,
    private val onCancel: () -> Unit = {},
) : NotificationDismissalPort {
    val cancelledKeys = mutableListOf<String>()

    override fun cancel(notificationKey: String) {
        if (shouldFail) error("listener disconnected")
        onCancel()
        cancelledKeys += notificationKey
    }
}

private class FakeNotificationRepository : NotificationRepository {
    val statuses = mutableMapOf<String, AutoDismissStatus>()

    override fun observeHistory(query: HistoryQuery): Flow<PagingData<NotificationThreadSummary>> = emptyFlow()
    override fun observeKnownPackages(): Flow<List<String>> = emptyFlow()
    override suspend fun findSnapshot(snapshotId: String): NotificationSnapshot? = null
    override suspend fun findLatestSnapshotByNotificationKey(notificationKey: String): NotificationSnapshot? = null
    override suspend fun appendCapture(capture: NotificationCapture) = Unit
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
    override suspend fun updateAutoDismissStatus(threadId: String, status: AutoDismissStatus) {
        statuses[threadId] = status
    }
    override suspend fun markRead(threadId: String, isRead: Boolean) = Unit
    override suspend fun markFavorite(threadId: String, isFavorite: Boolean) = Unit
    override suspend fun delete(threadId: String) = Unit
    override suspend fun deleteExpired(before: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private fun sampleSnapshot(isClearable: Boolean, isOngoing: Boolean): NotificationSnapshot = NotificationSnapshot(
    snapshotId = "snapshot-1",
    threadId = "thread-1",
    notificationKey = "key-1",
    userProfileKey = "user-0",
    packageName = "com.example.source",
    appLabel = "Source",
    title = "Title",
    titleBig = "",
    text = "Body",
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
    payloadAvailability = com.notifplus.domain.model.PayloadAvailability.AVAILABLE,
    previewText = "Body",
    category = "",
    channelId = "channel",
    channelName = "Channel",
    postedAt = 1L,
    capturedAt = 1L,
    isOngoing = isOngoing,
    isClearable = isClearable,
    isGroupSummary = false,
    isConversation = false,
    importance = null,
    rank = null,
    captureOrigin = CaptureOrigin.LIVE_POST,
)
