package com.notifplus.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RemovalReason {
    UNKNOWN,
    CLICK,
    CANCEL,
    CANCEL_ALL,
    ERROR,
    PACKAGE_CHANGED,
    USER_STOPPED,
    PACKAGE_BANNED,
    APP_CANCEL,
    APP_CANCEL_ALL,
    LISTENER_CANCEL,
    GROUP_SUMMARY_CANCELED,
    GROUP_OPTIMIZATION,
    PACKAGE_SUSPENDED,
    PROFILE_TURNED_OFF,
    UNAUTOBUNDLED,
    CHANNEL_BANNED,
    SNOOZED,
    TIMEOUT,
    CHANNEL_REMOVED,
    CLEAR_DATA,
    ASSISTANT_CANCEL,
    LOCKDOWN,
    BUNDLE_DISMISSED,
}

@Serializable
enum class RemovalOrigin {
    USER,
    SOURCE_APP,
    NOTIFPLUS,
    SYSTEM,
    UNKNOWN,
}

@Serializable
enum class AutoDismissStatus {
    NOT_REQUESTED,
    SKIPPED_NOT_CLEARABLE,
    REQUESTED,
    REMOVED_BY_NOTIFPLUS,
    FAILED,
}

@Serializable
enum class CaptureOrigin {
    LIVE_POST,
    RECONNECTED,
    LEGACY,
}

@Serializable
enum class PayloadAvailability {
    AVAILABLE,
    NO_TEXT_IN_DELIVERED_PAYLOAD,
    SYSTEM_REDACTED_OR_UNAVAILABLE,
    ATTACHMENT_UNAVAILABLE,
}

data class HistoryQuery(
    val searchText: String = "",
    val packageName: String? = null,
)

data class AutoDismissRule(
    val packageName: String,
    val enabled: Boolean,
)

data class RetentionSettings(
    val enabled: Boolean = true,
    val days: Int = DEFAULT_RETENTION_DAYS,
) {
    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
        const val MANUAL_ONLY = -1
        val OPTIONS = listOf(7, 30, 90, MANUAL_ONLY)
    }
}

@Serializable
data class NotificationMessage(
    val messageId: String,
    val snapshotId: String,
    val isHistoric: Boolean,
    val ordinal: Int,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val mimeType: String,
    val dataUri: String,
)

@Serializable
enum class AttachmentKind {
    MESSAGE_MEDIA,
    BIG_PICTURE,
    LARGE_ICON,
    LARGE_ICON_BIG,
    BACKGROUND_IMAGE,
    UNKNOWN,
}

@Serializable
enum class AttachmentSourceType {
    CONTENT_URI,
    INLINE_BITMAP,
    INLINE_ICON,
    UNKNOWN,
}

@Serializable
enum class AttachmentReadStatus {
    COPIED,
    UNAVAILABLE_AT_CAPTURE,
    TOO_LARGE,
    UNSUPPORTED_TYPE,
    IMPORTED_METADATA,
}

@Serializable
data class NotificationAttachment(
    val attachmentId: String,
    val snapshotId: String,
    val kind: AttachmentKind = AttachmentKind.UNKNOWN,
    val sourceType: AttachmentSourceType = AttachmentSourceType.CONTENT_URI,
    val sourceUri: String,
    val localPath: String,
    val mimeType: String,
    val contentDescription: String = "",
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
    val sizeBytes: Long,
    val sha256: String,
    val readStatus: AttachmentReadStatus = AttachmentReadStatus.IMPORTED_METADATA,
)

@Serializable
data class NotificationSnapshot(
    val snapshotId: String,
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
    val textLines: List<String>,
    val remoteInputHistory: List<String>,
    val structuredExtrasJson: String,
    val payloadAvailability: PayloadAvailability,
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
    val captureOrigin: CaptureOrigin,
)

@Serializable
data class NotificationThreadSummary(
    val threadId: String,
    val notificationKey: String,
    val userProfileKey: String,
    val packageName: String,
    val appLabel: String,
    val latestSnapshotId: String,
    val latestCapturedAt: Long,
    val latestPostedAt: Long,
    val latestTitle: String,
    val latestPreviewText: String,
    val latestPayloadAvailability: PayloadAvailability,
    val revisionCount: Int,
    val isActive: Boolean,
    val removedAt: Long?,
    val removalReason: RemovalReason,
    val removalReasonCode: Int?,
    val removalOrigin: RemovalOrigin,
    val isRead: Boolean,
    val isFavorite: Boolean,
    val autoDismissStatus: AutoDismissStatus,
)

@Serializable
data class NotificationThreadDetail(
    val summary: NotificationThreadSummary,
    val snapshots: List<NotificationSnapshotWithRelations>,
)

@Serializable
data class NotificationArchive(
    val schemaVersion: Int = 3,
    val threads: List<NotificationThreadDetail>,
)

@Serializable
data class NotificationSnapshotWithRelations(
    val snapshot: NotificationSnapshot,
    val messages: List<NotificationMessage>,
    val attachments: List<NotificationAttachment>,
)

data class NotificationAttachmentCandidate(
    val attachmentId: String,
    val snapshotId: String,
    val kind: AttachmentKind,
    val sourceType: AttachmentSourceType,
    val sourceUri: String,
    val mimeType: String,
    val contentDescription: String = "",
    val inlineBytes: ByteArray? = null,
    val readStatus: AttachmentReadStatus? = null,
)

data class NotificationCapture(
    val snapshot: NotificationSnapshot,
    val messages: List<NotificationMessage>,
    val attachmentCandidates: List<NotificationAttachmentCandidate>,
)
