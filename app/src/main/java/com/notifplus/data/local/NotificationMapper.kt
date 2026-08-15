package com.notifplus.data.local

import com.notifplus.domain.model.AutoDismissStatus
import com.notifplus.domain.model.AttachmentKind
import com.notifplus.domain.model.AttachmentReadStatus
import com.notifplus.domain.model.AttachmentSourceType
import com.notifplus.domain.model.CaptureOrigin
import com.notifplus.domain.model.NotificationAttachment
import com.notifplus.domain.model.NotificationMessage
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.model.PayloadAvailability
import com.notifplus.domain.model.RemovalOrigin
import com.notifplus.domain.model.RemovalReason
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val mapperJson = Json { ignoreUnknownKeys = true }

fun NotificationThreadEntity.toDomain(): NotificationThreadSummary = NotificationThreadSummary(
    threadId = threadId,
    notificationKey = notificationKey,
    userProfileKey = userProfileKey,
    packageName = packageName,
    appLabel = appLabel,
    latestSnapshotId = latestSnapshotId.orEmpty(),
    latestCapturedAt = latestCapturedAt,
    latestPostedAt = latestPostedAt,
    latestTitle = "",
    latestPreviewText = "",
    latestPayloadAvailability = PayloadAvailability.NO_TEXT_IN_DELIVERED_PAYLOAD,
    revisionCount = revisionCount,
    isActive = isActive,
    removedAt = removedAt,
    removalReason = enumValueOrDefault(removalReason, RemovalReason.UNKNOWN),
    removalReasonCode = removalReasonCode,
    removalOrigin = enumValueOrDefault(removalOrigin, RemovalOrigin.UNKNOWN),
    isRead = isRead,
    isFavorite = isFavorite,
    autoDismissStatus = enumValueOrDefault(autoDismissStatus, AutoDismissStatus.NOT_REQUESTED),
)

fun NotificationThreadSummaryRow.toDomain(): NotificationThreadSummary = NotificationThreadSummary(
    threadId = threadId,
    notificationKey = notificationKey,
    userProfileKey = userProfileKey,
    packageName = packageName,
    appLabel = appLabel,
    latestSnapshotId = latestSnapshotId,
    latestCapturedAt = latestCapturedAt,
    latestPostedAt = latestPostedAt,
    latestTitle = latestTitle,
    latestPreviewText = latestPreviewText,
    latestPayloadAvailability = enumValueOrDefault(
        latestPayloadAvailability,
        PayloadAvailability.NO_TEXT_IN_DELIVERED_PAYLOAD,
    ),
    revisionCount = revisionCount,
    isActive = isActive,
    removedAt = removedAt,
    removalReason = enumValueOrDefault(removalReason, RemovalReason.UNKNOWN),
    removalReasonCode = removalReasonCode,
    removalOrigin = enumValueOrDefault(removalOrigin, RemovalOrigin.UNKNOWN),
    isRead = isRead,
    isFavorite = isFavorite,
    autoDismissStatus = enumValueOrDefault(autoDismissStatus, AutoDismissStatus.NOT_REQUESTED),
)

fun NotificationSnapshotEntity.toDomain(): NotificationSnapshot = NotificationSnapshot(
    snapshotId = snapshotId,
    threadId = threadId,
    notificationKey = notificationKey,
    userProfileKey = userProfileKey,
    packageName = packageName,
    appLabel = appLabel,
    title = title,
    titleBig = titleBig,
    text = text,
    bigText = bigText,
    subText = subText,
    infoText = infoText,
    summaryText = summaryText,
    conversationTitle = conversationTitle,
    tickerText = tickerText,
    template = template,
    textLines = decodeList(textLinesJson),
    remoteInputHistory = decodeList(remoteInputHistoryJson),
    structuredExtrasJson = structuredExtrasJson,
    payloadAvailability = enumValueOrDefault(
        payloadAvailability,
        PayloadAvailability.NO_TEXT_IN_DELIVERED_PAYLOAD,
    ),
    previewText = previewText,
    category = category,
    channelId = channelId,
    channelName = channelName,
    postedAt = postedAt,
    capturedAt = capturedAt,
    isOngoing = isOngoing,
    isClearable = isClearable,
    isGroupSummary = isGroupSummary,
    isConversation = isConversation,
    importance = importance,
    rank = rank,
    captureOrigin = enumValueOrDefault(captureOrigin, CaptureOrigin.LEGACY),
)

fun NotificationMessageEntity.toDomain(): NotificationMessage = NotificationMessage(
    messageId = messageId,
    snapshotId = snapshotId,
    isHistoric = isHistoric,
    ordinal = ordinal,
    sender = sender,
    text = text,
    timestamp = timestamp,
    mimeType = mimeType,
    dataUri = dataUri,
)

fun NotificationAttachmentEntity.toDomain(): NotificationAttachment = NotificationAttachment(
    attachmentId = attachmentId,
    snapshotId = snapshotId,
    kind = enumValueOrDefault(kind, AttachmentKind.UNKNOWN),
    sourceType = enumValueOrDefault(sourceType, AttachmentSourceType.UNKNOWN),
    sourceUri = sourceUri,
    localPath = localPath,
    mimeType = mimeType,
    contentDescription = contentDescription,
    pixelWidth = pixelWidth,
    pixelHeight = pixelHeight,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    readStatus = enumValueOrDefault(readStatus, AttachmentReadStatus.UNAVAILABLE_AT_CAPTURE),
)

fun NotificationSnapshot.toEntity(): NotificationSnapshotEntity = NotificationSnapshotEntity(
    snapshotId = snapshotId,
    threadId = threadId,
    notificationKey = notificationKey,
    userProfileKey = userProfileKey,
    packageName = packageName,
    appLabel = appLabel,
    title = title,
    titleBig = titleBig,
    text = text,
    bigText = bigText,
    subText = subText,
    infoText = infoText,
    summaryText = summaryText,
    conversationTitle = conversationTitle,
    tickerText = tickerText,
    template = template,
    textLinesJson = mapperJson.encodeToString(textLines),
    remoteInputHistoryJson = mapperJson.encodeToString(remoteInputHistory),
    structuredExtrasJson = structuredExtrasJson,
    payloadAvailability = payloadAvailability.name,
    previewText = previewText,
    category = category,
    channelId = channelId,
    channelName = channelName,
    postedAt = postedAt,
    capturedAt = capturedAt,
    isOngoing = isOngoing,
    isClearable = isClearable,
    isGroupSummary = isGroupSummary,
    isConversation = isConversation,
    importance = importance,
    rank = rank,
    captureOrigin = captureOrigin.name,
)

fun NotificationMessage.toEntity(): NotificationMessageEntity = NotificationMessageEntity(
    messageId = messageId,
    snapshotId = snapshotId,
    isHistoric = isHistoric,
    ordinal = ordinal,
    sender = sender,
    text = text,
    timestamp = timestamp,
    mimeType = mimeType,
    dataUri = dataUri,
)

fun NotificationAttachment.toEntity(): NotificationAttachmentEntity = NotificationAttachmentEntity(
    attachmentId = attachmentId,
    snapshotId = snapshotId,
    kind = kind.name,
    sourceType = sourceType.name,
    sourceUri = sourceUri,
    localPath = localPath,
    mimeType = mimeType,
    contentDescription = contentDescription,
    pixelWidth = pixelWidth,
    pixelHeight = pixelHeight,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    readStatus = readStatus.name,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)

private fun decodeList(value: String): List<String> =
    runCatching { mapperJson.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
