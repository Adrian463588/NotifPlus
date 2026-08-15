package com.notifplus.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.notifplus.domain.model.AttachmentReadStatus
import com.notifplus.domain.model.NotificationAttachment
import com.notifplus.domain.model.NotificationAttachmentCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

class NotificationAttachmentFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun persist(candidates: List<NotificationAttachmentCandidate>): List<NotificationAttachment> =
        withContext(Dispatchers.IO) {
            candidates.map(::persistOne)
        }

    suspend fun delete(paths: List<String>) = withContext(Dispatchers.IO) {
        val root = attachmentDirectory().canonicalFile.toPath()
        paths.filter(String::isNotBlank).forEach { path ->
            runCatching { File(path).canonicalFile.toPath() }
                .getOrNull()
                ?.takeIf { it.startsWith(root) }
                ?.toFile()
                ?.delete()
        }
    }

    private fun persistOne(candidate: NotificationAttachmentCandidate): NotificationAttachment {
        val base = candidate.toAttachmentBase()
        candidate.readStatus?.let { return base.withStatus(it) }
        if (candidate.inlineBytes?.isEmpty() == true ||
            candidate.inlineBytes == null && candidate.sourceUri.isBlank()
        ) {
            return base.withStatus(AttachmentReadStatus.UNAVAILABLE_AT_CAPTURE)
        }

        val directory = attachmentDirectory().apply { mkdirs() }
        val temporary = File(directory, ".${UUID.randomUUID()}.tmp")
        val mimeType = candidate.mimeType.ifBlank { resolveMimeType(candidate.sourceUri) }
        val target = File(directory, "${candidate.attachmentId}${mimeExtension(mimeType)}")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            FileOutputStream(temporary).use { rawOutput ->
                BufferedOutputStream(rawOutput).use { output ->
                    openInput(candidate).use { rawInput ->
                        BufferedInputStream(rawInput).use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count
                                if (size > MAX_ATTACHMENT_BYTES) throw AttachmentTooLargeException()
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                            }
                        }
                    }
                }
            }
            if (!temporary.renameTo(target)) error("Unable to commit attachment file")
            val dimensions = readDimensions(target, mimeType)
            base.copy(
                localPath = target.canonicalPath,
                mimeType = mimeType,
                pixelWidth = dimensions?.first,
                pixelHeight = dimensions?.second,
                sizeBytes = size,
                sha256 = digest.digest().toHexString(),
                readStatus = AttachmentReadStatus.COPIED,
            )
        } catch (_: AttachmentTooLargeException) {
            temporary.delete()
            base.withStatus(AttachmentReadStatus.TOO_LARGE)
        } catch (_: Throwable) {
            temporary.delete()
            target.delete()
            base.withStatus(AttachmentReadStatus.UNAVAILABLE_AT_CAPTURE)
        }
    }

    private fun openInput(candidate: NotificationAttachmentCandidate) =
        candidate.inlineBytes?.let(::ByteArrayInputStream)
            ?: context.contentResolver.openInputStream(Uri.parse(candidate.sourceUri))
            ?: error("Attachment URI is unavailable")

    private fun resolveMimeType(sourceUri: String): String = runCatching {
        context.contentResolver.getType(Uri.parse(sourceUri)).orEmpty()
    }.getOrDefault("")

    private fun mimeExtension(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> ".png"
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/heic" -> ".heic"
        else -> ".bin"
    }

    private fun readDimensions(file: File, mimeType: String): Pair<Int, Int>? {
        if (!mimeType.startsWith("image/")) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(file).use { input -> BitmapFactory.decodeStream(input, null, options) }
        return options.outWidth.takeIf { it > 0 }?.let { width ->
            options.outHeight.takeIf { it > 0 }?.let { height -> width to height }
        }
    }

    private fun attachmentDirectory(): File = context.filesDir.resolve("notification_attachments")

    private companion object {
        const val MAX_ATTACHMENT_BYTES = 50L * 1024L * 1024L
    }
}

private fun NotificationAttachmentCandidate.toAttachmentBase() = NotificationAttachment(
    attachmentId = attachmentId,
    snapshotId = snapshotId,
    kind = kind,
    sourceType = sourceType,
    sourceUri = sourceUri,
    localPath = "",
    mimeType = mimeType,
    contentDescription = contentDescription,
    pixelWidth = null,
    pixelHeight = null,
    sizeBytes = 0L,
    sha256 = "",
    readStatus = readStatus ?: AttachmentReadStatus.UNAVAILABLE_AT_CAPTURE,
)

private fun NotificationAttachment.withStatus(status: AttachmentReadStatus) = copy(
    readStatus = status,
)

private class AttachmentTooLargeException : Exception()

private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte) }
