package com.notifplus.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.notifplus.domain.model.AutoDismissStatus
import com.notifplus.domain.model.CaptureOrigin
import com.notifplus.domain.model.NotificationArchive
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.NotificationSnapshotWithRelations
import com.notifplus.domain.model.NotificationThreadDetail
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.model.PayloadAvailability
import com.notifplus.domain.model.RemovalOrigin
import com.notifplus.domain.model.RemovalReason
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class NotificationArchiveTransferTest {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun `archive serializes and deserializes accurately`() {
        val sampleThread = NotificationThreadDetail(
            summary = NotificationThreadSummary(
                threadId = "t1",
                notificationKey = "k1",
                userProfileKey = "0",
                packageName = "com.whatsapp",
                appLabel = "WhatsApp",
                latestSnapshotId = "s1",
                latestCapturedAt = 1000L,
                latestPostedAt = 900L,
                latestTitle = "John Doe",
                latestPreviewText = "Halo, apa kabar?",
                latestPayloadAvailability = PayloadAvailability.AVAILABLE,
                revisionCount = 1,
                isActive = true,
                removedAt = null,
                removalReason = RemovalReason.UNKNOWN,
                removalReasonCode = null,
                removalOrigin = RemovalOrigin.UNKNOWN,
                isRead = false,
                isFavorite = true,
                autoDismissStatus = AutoDismissStatus.NOT_REQUESTED,
            ),
            snapshots = listOf(
                NotificationSnapshotWithRelations(
                    snapshot = NotificationSnapshot(
                        snapshotId = "s1",
                        threadId = "t1",
                        notificationKey = "k1",
                        userProfileKey = "0",
                        packageName = "com.whatsapp",
                        appLabel = "WhatsApp",
                        title = "John Doe",
                        titleBig = "",
                        text = "Halo, apa kabar?",
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
                        previewText = "Halo, apa kabar?",
                        category = "msg",
                        channelId = "chat",
                        channelName = "Chats",
                        postedAt = 900L,
                        capturedAt = 1000L,
                        isOngoing = false,
                        isClearable = true,
                        isGroupSummary = false,
                        isConversation = true,
                        importance = 3,
                        rank = 1,
                        captureOrigin = CaptureOrigin.LIVE_POST,
                    ),
                    messages = emptyList(),
                    attachments = emptyList(),
                ),
            ),
        )


        val archive = NotificationArchive(schemaVersion = 3, threads = listOf(sampleThread))
        val serialized = json.encodeToString(archive)
        val deserialized = json.decodeFromString<NotificationArchive>(serialized)

        assertThat(deserialized.schemaVersion).isEqualTo(3)
        assertThat(deserialized.threads).hasSize(1)
        assertThat(deserialized.threads.first().summary.appLabel).isEqualTo("WhatsApp")
        assertThat(deserialized.threads.first().summary.latestPreviewText).isEqualTo("Halo, apa kabar?")
        assertThat(deserialized.threads.first().summary.isFavorite).isTrue()
    }
}
