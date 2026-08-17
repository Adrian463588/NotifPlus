package com.notifplus.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.notifplus.domain.model.AttachmentKind
import com.notifplus.domain.model.AttachmentReadStatus
import com.notifplus.domain.model.AttachmentSourceType
import com.notifplus.domain.model.CaptureOrigin
import com.notifplus.domain.model.NotificationAttachmentCandidate
import com.notifplus.domain.model.NotificationCapture
import com.notifplus.domain.model.NotificationMessage
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.PayloadAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.inject.Inject
import java.util.UUID

class NotificationPayloadExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { encodeDefaults = true }

    fun extract(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap?,
        captureOrigin: CaptureOrigin,
    ): NotificationCapture {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle()
        val snapshotId = UUID.randomUUID().toString()
        val messages = extractMessages(notification, extras, sbn.key, snapshotId)
        val title = extras.text(Notification.EXTRA_TITLE)
        val titleBig = extras.text(Notification.EXTRA_TITLE_BIG)
        val text = extras.text(Notification.EXTRA_TEXT)
        val bigText = extras.text(Notification.EXTRA_BIG_TEXT)
        val subText = extras.text(Notification.EXTRA_SUB_TEXT)
        val infoText = extras.text(Notification.EXTRA_INFO_TEXT)
        val summaryText = extras.text(Notification.EXTRA_SUMMARY_TEXT)
        val conversationTitle = extras.text(Notification.EXTRA_CONVERSATION_TITLE)
        val tickerText = notification.tickerText?.toString().orEmpty()
        val textLines = extras.charSequenceList(Notification.EXTRA_TEXT_LINES)
        val remoteInputHistory = extras.charSequenceList(Notification.EXTRA_REMOTE_INPUT_HISTORY)
        val ranking = NotificationListenerService.Ranking().takeIf { current ->
            rankingMap?.getRanking(sbn.key, current) == true
        }
        val userProfileKey = sbn.user.toString()
        val threadId = "$userProfileKey:${sbn.key}"
        val capturedAt = System.currentTimeMillis()
        val latestMessageText = messages.asSequence()
            .filterNot(NotificationMessage::isHistoric)
            .map(NotificationMessage::text)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
        val previewText = listOf(
            bigText,
            text,
            textLines.joinToString("\n"),
            latestMessageText,
            subText,
            infoText,
            summaryText,
        ).firstOrNull(String::isNotBlank).orEmpty()
        val hasText = listOf(
            title,
            titleBig,
            text,
            bigText,
            subText,
            infoText,
            summaryText,
            conversationTitle,
            tickerText,
            previewText,
            *textLines.toTypedArray(),
            *remoteInputHistory.toTypedArray(),
            *messages.map(NotificationMessage::text).toTypedArray(),
        ).any(String::isNotBlank)
        val snapshot = NotificationSnapshot(
            snapshotId = snapshotId,
            threadId = threadId,
            notificationKey = sbn.key,
            userProfileKey = userProfileKey,
            packageName = sbn.packageName,
            appLabel = resolveAppLabel(sbn.packageName),
            title = title,
            titleBig = titleBig,
            text = text,
            bigText = bigText,
            subText = subText,
            infoText = infoText,
            summaryText = summaryText,
            conversationTitle = conversationTitle,
            tickerText = tickerText,
            template = extras.text(Notification.EXTRA_TEMPLATE),
            textLines = textLines,
            remoteInputHistory = remoteInputHistory,
            structuredExtrasJson = encodeTextualExtras(extras),
            payloadAvailability = if (hasText) {
                PayloadAvailability.AVAILABLE
            } else {
                PayloadAvailability.NO_TEXT_IN_DELIVERED_PAYLOAD
            },
            previewText = previewText,
            category = notification.category.orEmpty(),
            channelId = ranking?.channel?.id.orEmpty(),
            channelName = ranking?.channel?.name?.toString().orEmpty(),
            postedAt = sbn.postTime,
            capturedAt = capturedAt,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isConversation = notification.category == Notification.CATEGORY_MESSAGE,
            importance = ranking?.importance,
            rank = ranking?.rank,
            captureOrigin = captureOrigin,
        )
        val candidates = extractAttachments(notification, extras, messages, snapshotId)
        return NotificationCapture(snapshot, messages, candidates)
    }

    fun recordThreadId(sbn: StatusBarNotification): String = "${sbn.user}:${sbn.key}"

    private fun extractAttachments(
        notification: Notification,
        extras: Bundle,
        messages: List<NotificationMessage>,
        snapshotId: String,
    ): List<NotificationAttachmentCandidate> {
        val pictureContentDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            extras.text(Notification.EXTRA_PICTURE_CONTENT_DESCRIPTION)
        } else {
            ""
        }
        val candidates = buildList {
            messages.filter { it.dataUri.isNotBlank() }.forEach { message ->
                add(
                    NotificationAttachmentCandidate(
                        attachmentId = UUID.randomUUID().toString(),
                        snapshotId = snapshotId,
                        kind = AttachmentKind.MESSAGE_MEDIA,
                        sourceType = AttachmentSourceType.CONTENT_URI,
                        sourceUri = message.dataUri,
                        mimeType = message.mimeType,
                    ),
                )
            }
            addInlineOrUri(
                value = extras.value(Notification.EXTRA_PICTURE),
                kind = AttachmentKind.BIG_PICTURE,
                snapshotId = snapshotId,
                contentDescription = pictureContentDescription,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addInlineOrUri(
                    value = extras.parcelableCompat(Notification.EXTRA_PICTURE_ICON, Icon::class.java),
                    kind = AttachmentKind.BIG_PICTURE,
                    snapshotId = snapshotId,
                    contentDescription = pictureContentDescription,
                )
            }
            addInlineOrUri(
                value = extras.value(Notification.EXTRA_LARGE_ICON),
                kind = AttachmentKind.LARGE_ICON,
                snapshotId = snapshotId,
            )
            addInlineOrUri(
                value = extras.value(Notification.EXTRA_LARGE_ICON_BIG),
                kind = AttachmentKind.LARGE_ICON_BIG,
                snapshotId = snapshotId,
            )
            notification.getLargeIcon()?.let { icon ->
                addInlineOrUri(icon, AttachmentKind.LARGE_ICON, snapshotId)
            }
            extras.valueAsUriString(Notification.EXTRA_BACKGROUND_IMAGE_URI)
                .takeIf(String::isNotBlank)
                ?.let { uri ->
                    add(
                        NotificationAttachmentCandidate(
                            attachmentId = UUID.randomUUID().toString(),
                            snapshotId = snapshotId,
                            kind = AttachmentKind.BACKGROUND_IMAGE,
                            sourceType = AttachmentSourceType.CONTENT_URI,
                            sourceUri = uri,
                            mimeType = "image/*",
                        ),
                    )
                }
        }
        return candidates.distinctBy { candidate ->
            listOf(
                candidate.kind,
                candidate.sourceUri,
                candidate.inlineBytes?.contentHashCode(),
            )
        }
    }

    private fun MutableList<NotificationAttachmentCandidate>.addInlineOrUri(
        value: Any?,
        kind: AttachmentKind,
        snapshotId: String,
        contentDescription: String = "",
    ) {
        when (value) {
            is Bitmap -> encodeBitmap(value).let { encoded ->
                add(
                    NotificationAttachmentCandidate(
                        attachmentId = UUID.randomUUID().toString(),
                        snapshotId = snapshotId,
                        kind = kind,
                        sourceType = AttachmentSourceType.INLINE_BITMAP,
                        sourceUri = "",
                        mimeType = "image/png",
                        contentDescription = contentDescription,
                        inlineBytes = encoded.bytes,
                        readStatus = encoded.status,
                    ),
                )
            }
            is Icon -> addIcon(value, kind, snapshotId, contentDescription)
        }
    }

    private fun MutableList<NotificationAttachmentCandidate>.addIcon(
        icon: Icon,
        kind: AttachmentKind,
        snapshotId: String,
        contentDescription: String,
    ) {
        val uri = runCatching { icon.uri?.toString().orEmpty() }.getOrDefault("")
        if (uri.isNotBlank() && (icon.type == Icon.TYPE_URI || icon.type == Icon.TYPE_URI_ADAPTIVE_BITMAP)) {
            add(
                NotificationAttachmentCandidate(
                    attachmentId = UUID.randomUUID().toString(),
                    snapshotId = snapshotId,
                    kind = kind,
                    sourceType = AttachmentSourceType.CONTENT_URI,
                    sourceUri = uri,
                    mimeType = context.contentResolver.getType(android.net.Uri.parse(uri)).orEmpty().ifBlank { "image/*" },
                    contentDescription = contentDescription,
                ),
            )
            return
        }
        drawableToBytes(runCatching { icon.loadDrawable(context) }.getOrNull()).let { encoded ->
            add(
                NotificationAttachmentCandidate(
                    attachmentId = UUID.randomUUID().toString(),
                    snapshotId = snapshotId,
                    kind = kind,
                    sourceType = AttachmentSourceType.INLINE_ICON,
                    sourceUri = "",
                    mimeType = "image/png",
                    contentDescription = contentDescription,
                    inlineBytes = encoded.bytes,
                    readStatus = encoded.status,
                ),
            )
        }
    }

    private fun encodeBitmap(bitmap: Bitmap): EncodedInlineAttachment = runCatching {
        LimitedByteArrayOutputStream(MAX_ATTACHMENT_BYTES).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            EncodedInlineAttachment(output.toByteArray(), null)
        }
    }.getOrElse { error ->
        if (error is InlineAttachmentTooLargeException) {
            EncodedInlineAttachment(null, AttachmentReadStatus.TOO_LARGE)
        } else {
            EncodedInlineAttachment(null, AttachmentReadStatus.UNSUPPORTED_TYPE)
        }
    }

    private fun drawableToBytes(drawable: Drawable?): EncodedInlineAttachment {
        drawable ?: return EncodedInlineAttachment(null, AttachmentReadStatus.UNAVAILABLE_AT_CAPTURE)
        val bitmapDrawable = drawable as? BitmapDrawable
        val bitmap = bitmapDrawable?.bitmap ?: runCatching {
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
                Canvas(target).also { canvas ->
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
        }.getOrNull() ?: return EncodedInlineAttachment(null, AttachmentReadStatus.UNSUPPORTED_TYPE)
        return encodeBitmap(bitmap)
    }

    private fun extractMessages(
        notification: Notification,
        extras: Bundle,
        key: String,
        snapshotId: String,
    ): List<NotificationMessage> {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (style != null && (style.messages.isNotEmpty() || style.historicMessages.isNotEmpty())) {
            val current = style.messages.mapIndexed { index, message ->
                NotificationMessage(
                    messageId = "$snapshotId:current:$index:$key",
                    snapshotId = snapshotId,
                    isHistoric = false,
                    ordinal = index,
                    sender = message.person?.name?.toString().orEmpty(),
                    text = message.text?.toString().orEmpty(),
                    timestamp = message.timestamp,
                    mimeType = message.dataMimeType.orEmpty(),
                    dataUri = message.dataUri?.toString().orEmpty(),
                )
            }
            val historic = style.historicMessages.mapIndexed { index, message ->
                NotificationMessage(
                    messageId = "$snapshotId:historic:$index:$key",
                    snapshotId = snapshotId,
                    isHistoric = true,
                    ordinal = index,
                    sender = message.person?.name?.toString().orEmpty(),
                    text = message.text?.toString().orEmpty(),
                    timestamp = message.timestamp,
                    mimeType = message.dataMimeType.orEmpty(),
                    dataUri = message.dataUri?.toString().orEmpty(),
                )
            }
            return current + historic
        }

        val current = extras.nativeMessagingMessages(Notification.EXTRA_MESSAGES)
        val historic = extras.nativeMessagingMessages(Notification.EXTRA_HISTORIC_MESSAGES)
        return current.mapIndexed { index, message ->
            message.toDomain(snapshotId, key, false, index)
        } + historic.mapIndexed { index, message ->
            message.toDomain(snapshotId, key, true, index)
        }
    }

    private fun NativeMessagingMessage.toDomain(
        snapshotId: String,
        key: String,
        isHistoric: Boolean,
        ordinal: Int,
    ) = NotificationMessage(
        messageId = "$snapshotId:${if (isHistoric) "historic" else "current"}:$ordinal:$key",
        snapshotId = snapshotId,
        isHistoric = isHistoric,
        ordinal = ordinal,
        sender = sender,
        text = text,
        timestamp = timestamp,
        mimeType = mimeType,
        dataUri = dataUri,
    )

    private fun encodeTextualExtras(extras: Bundle): String =
        json.encodeToString(JsonObject(extras.keySet().sorted().mapNotNull { key ->
            textualJsonElement(key, extras.value(key))?.let { key to it }
        }.toMap()))

    private fun textualJsonElement(key: String, value: Any?): JsonElement? = when (value) {
        null -> null
        is CharSequence -> JsonPrimitive(value.toString())
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Bundle -> JsonObject(value.keySet().sorted().mapNotNull { child ->
            textualJsonElement(child, value.value(child))?.let { child to it }
        }.toMap())
        is Array<*> -> JsonArray(value.mapNotNull { textualJsonElement(key, it) })
        is List<*> -> JsonArray(value.mapNotNull { textualJsonElement(key, it) })
        else -> null
    }

    private fun resolveAppLabel(packageName: String): String = runCatching {
        @Suppress("DEPRECATION")
        val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrDefault(packageName)

    private data class EncodedInlineAttachment(
        val bytes: ByteArray?,
        val status: AttachmentReadStatus?,
    )

    private companion object {
        const val MAX_ATTACHMENT_BYTES = 50L * 1024L * 1024L
    }
}

@Suppress("DEPRECATION")
private fun Bundle.value(key: String): Any? = get(key)

@Suppress("DEPRECATION")
private fun Bundle.valueAsUriString(key: String): String = when (val value = get(key)) {
    is android.net.Uri -> value.toString()
    is CharSequence -> value.toString()
    else -> ""
}

@Suppress("DEPRECATION")
private fun <T : Parcelable> Bundle.parcelableCompat(key: String, clazz: Class<T>): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, clazz)
    } else {
        getParcelable(key) as? T
    }

@Suppress("DEPRECATION")
private data class NativeMessagingMessage(
    val text: String,
    val timestamp: Long,
    val sender: String,
    val mimeType: String,
    val dataUri: String,
)

private fun Bundle.nativeMessagingMessages(key: String): List<NativeMessagingMessage> = runCatching {
    val array = getParcelableArray(key)
        ?.toList()
        ?.mapNotNull { item -> (item as? Bundle)?.toNativeMessagingMessage() }
        .orEmpty()
    if (array.isNotEmpty()) return@runCatching array
    getParcelableArrayList<Parcelable>(key)
        ?.toList()
        ?.mapNotNull { item -> (item as? Bundle)?.toNativeMessagingMessage() }
        .orEmpty()
}.getOrDefault(emptyList())

@Suppress("DEPRECATION")
private fun Bundle.toNativeMessagingMessage(): NativeMessagingMessage? {
    if (!containsKey("text") || !containsKey("time")) return null
    val text = getCharSequence("text")?.toString().orEmpty()
    val timestamp = getLong("time")
    val sender = getCharSequence("sender")?.toString().orEmpty()
        .ifBlank {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val person = getParcelable("sender_person") as? android.app.Person
                person?.name?.toString().orEmpty()
            } else {
                ""
            }
        }
    val mimeType = getString("type").orEmpty()
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable("uri", Uri::class.java)
    } else {
        getParcelable("uri") as? Uri
    }
    return NativeMessagingMessage(
        text = text,
        timestamp = timestamp,
        sender = sender,
        mimeType = mimeType,
        dataUri = uri?.toString().orEmpty(),
    )
}

private fun Bundle.text(key: String): String = getCharSequence(key)?.toString().orEmpty()

private fun Bundle.charSequenceList(key: String): List<String> =
    getCharSequenceArray(key)?.map(CharSequence::toString).orEmpty()

private class LimitedByteArrayOutputStream(
    private val maxBytes: Long,
) : OutputStream() {
    private val delegate = ByteArrayOutputStream()
    private var size = 0L

    override fun write(oneByte: Int) {
        ensureCapacity(1)
        delegate.write(oneByte)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        ensureCapacity(length.toLong())
        delegate.write(bytes, offset, length)
    }

    fun toByteArray(): ByteArray = delegate.toByteArray()

    override fun close() = delegate.close()

    private fun ensureCapacity(incoming: Long) {
        if (size + incoming > maxBytes) throw InlineAttachmentTooLargeException()
        size += incoming
    }
}

private class InlineAttachmentTooLargeException : Exception()
