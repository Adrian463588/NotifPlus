package com.notifplus.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_threads",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["notificationKey"]),
        Index(value = ["latestCapturedAt"]),
    ],
)
data class NotificationThreadEntity(
    @PrimaryKey val threadId: String,
    val notificationKey: String,
    val userProfileKey: String,
    val packageName: String,
    val appLabel: String,
    val latestSnapshotId: String?,
    val latestCapturedAt: Long,
    val latestPostedAt: Long,
    val revisionCount: Int,
    val isActive: Boolean,
    val removedAt: Long?,
    val removalReason: String,
    val removalReasonCode: Int?,
    val removalOrigin: String,
    val isRead: Boolean,
    val isFavorite: Boolean,
    val autoDismissStatus: String,
)

@Entity(
    tableName = "notification_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = NotificationThreadEntity::class,
            parentColumns = ["threadId"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["threadId", "capturedAt"]),
        Index(value = ["notificationKey"]),
        Index(value = ["packageName"]),
    ],
)
data class NotificationSnapshotEntity(
    @PrimaryKey val snapshotId: String,
    val threadId: String,
    val notificationKey: String,
    val userProfileKey: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val titleBig: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val infoText: String,
    val summaryText: String,
    val conversationTitle: String,
    val tickerText: String,
    val template: String,
    val textLinesJson: String,
    val remoteInputHistoryJson: String,
    val structuredExtrasJson: String,
    val payloadAvailability: String,
    val previewText: String,
    val category: String,
    val channelId: String,
    val channelName: String,
    val postedAt: Long,
    val capturedAt: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val isGroupSummary: Boolean,
    val isConversation: Boolean,
    val importance: Int?,
    val rank: Int?,
    val captureOrigin: String,
)

@Entity(
    tableName = "notification_messages",
    foreignKeys = [
        ForeignKey(
            entity = NotificationSnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["snapshotId"])],
)
data class NotificationMessageEntity(
    @PrimaryKey val messageId: String,
    val snapshotId: String,
    val isHistoric: Boolean,
    val ordinal: Int,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val mimeType: String,
    val dataUri: String,
)

@Entity(
    tableName = "notification_attachments",
    foreignKeys = [
        ForeignKey(
            entity = NotificationSnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["snapshotId"]), Index(value = ["sourceUri"])],
)
data class NotificationAttachmentEntity(
    @PrimaryKey val attachmentId: String,
    val snapshotId: String,
    val kind: String,
    val sourceType: String,
    val sourceUri: String,
    val localPath: String,
    val mimeType: String,
    val contentDescription: String,
    val pixelWidth: Int?,
    val pixelHeight: Int?,
    val sizeBytes: Long,
    val sha256: String,
    val readStatus: String,
)

data class NotificationThreadSummaryRow(
    val threadId: String,
    val notificationKey: String,
    val userProfileKey: String,
    val packageName: String,
    val appLabel: String,
    @ColumnInfo(name = "latestSnapshotId") val latestSnapshotId: String,
    @ColumnInfo(name = "latestCapturedAt") val latestCapturedAt: Long,
    @ColumnInfo(name = "latestPostedAt") val latestPostedAt: Long,
    @ColumnInfo(name = "latestTitle") val latestTitle: String,
    @ColumnInfo(name = "latestPreviewText") val latestPreviewText: String,
    @ColumnInfo(name = "latestPayloadAvailability") val latestPayloadAvailability: String,
    val revisionCount: Int,
    val isActive: Boolean,
    val removedAt: Long?,
    val removalReason: String,
    val removalReasonCode: Int?,
    val removalOrigin: String,
    val isRead: Boolean,
    val isFavorite: Boolean,
    val autoDismissStatus: String,
)
