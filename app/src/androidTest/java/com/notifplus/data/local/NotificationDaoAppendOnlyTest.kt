package com.notifplus.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class NotificationDaoAppendOnlyTest {
    private lateinit var database: NotifPlusDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotifPlusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sameNotificationKeyKeepsEverySnapshot() = runBlocking {
        val first = snapshot("snapshot-a", "pesan asli")
        val second = snapshot("snapshot-b", "This message was deleted")

        dao.appendCapture(thread(first), first, emptyList(), emptyList())
        dao.appendCapture(thread(second), second, emptyList(), emptyList())

        val snapshots = dao.snapshotsForThreadAscending(first.threadId)
        assertThat(snapshots.map { it.snapshotId }).containsExactly("snapshot-a", "snapshot-b").inOrder()
        assertThat(snapshots.first().text).isEqualTo("pesan asli")
        assertThat(snapshots.last().text).isEqualTo("This message was deleted")
        assertThat(dao.findThread(first.threadId)?.revisionCount).isEqualTo(2)
    }

    private fun thread(snapshot: NotificationSnapshotEntity) = NotificationThreadEntity(
        threadId = snapshot.threadId,
        notificationKey = snapshot.notificationKey,
        userProfileKey = snapshot.userProfileKey,
        packageName = snapshot.packageName,
        appLabel = snapshot.appLabel,
        latestSnapshotId = snapshot.snapshotId,
        latestCapturedAt = snapshot.capturedAt,
        latestPostedAt = snapshot.postedAt,
        revisionCount = 1,
        isActive = true,
        removedAt = null,
        removalReason = "UNKNOWN",
        removalReasonCode = null,
        removalOrigin = "UNKNOWN",
        isRead = false,
        isFavorite = false,
        autoDismissStatus = "NOT_REQUESTED",
    )

    private fun snapshot(id: String, text: String) = NotificationSnapshotEntity(
        snapshotId = id,
        threadId = "thread-1",
        notificationKey = "key-1",
        userProfileKey = "user-0",
        packageName = "com.example.source",
        appLabel = "Source",
        title = "Sender",
        titleBig = "",
        text = text,
        bigText = "",
        subText = "",
        infoText = "",
        summaryText = "",
        conversationTitle = "",
        tickerText = "",
        template = "",
        textLinesJson = "[]",
        remoteInputHistoryJson = "[]",
        structuredExtrasJson = "{}",
        payloadAvailability = "AVAILABLE",
        previewText = text,
        category = "msg",
        channelId = "channel",
        channelName = "Channel",
        postedAt = id.last().code.toLong(),
        capturedAt = id.last().code.toLong(),
        isOngoing = false,
        isClearable = true,
        isGroupSummary = false,
        isConversation = true,
        importance = null,
        rank = null,
        captureOrigin = "LIVE_POST",
    )
}
