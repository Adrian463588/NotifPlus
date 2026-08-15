package com.notifplus.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.notifplus.domain.model.AttachmentKind
import com.notifplus.domain.model.AttachmentReadStatus
import com.notifplus.domain.model.AttachmentSourceType
import com.notifplus.domain.model.NotificationAttachmentCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationAttachmentFileStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun copiedImageSurvivesSourceDeletion() = runBlocking {
        val source = File(context.cacheDir, "source-${UUID.randomUUID()}.png")
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { bitmap ->
            FileOutputStream(source).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            bitmap.recycle()
        }
        val store = NotificationAttachmentFileStore(context)
        val attachment = store.persist(
            listOf(
                NotificationAttachmentCandidate(
                    attachmentId = "attachment-${UUID.randomUUID()}",
                    snapshotId = "snapshot-1",
                    kind = AttachmentKind.BIG_PICTURE,
                    sourceType = AttachmentSourceType.CONTENT_URI,
                    sourceUri = Uri.fromFile(source).toString(),
                    mimeType = "image/png",
                ),
            ),
        ).single()

        assertThat(attachment.readStatus).isEqualTo(AttachmentReadStatus.COPIED)
        val localCopy = File(attachment.localPath)
        assertThat(localCopy.exists()).isTrue()
        source.delete()
        assertThat(localCopy.exists()).isTrue()
        assertThat(BitmapFactoryCompat.decode(localCopy)).isTrue()

        store.delete(listOf(attachment.localPath))
        assertThat(localCopy.exists()).isFalse()
    }

    @Test
    fun oversizedInlineAttachmentIsRecordedWithoutLocalFile() = runBlocking {
        val store = NotificationAttachmentFileStore(context)
        val attachment = store.persist(
            listOf(
                NotificationAttachmentCandidate(
                    attachmentId = "oversized-${UUID.randomUUID()}",
                    snapshotId = "snapshot-1",
                    kind = AttachmentKind.MESSAGE_MEDIA,
                    sourceType = AttachmentSourceType.INLINE_BITMAP,
                    sourceUri = "",
                    mimeType = "image/png",
                    inlineBytes = ByteArray(50 * 1024 * 1024 + 1),
                ),
            ),
        ).single()

        assertThat(attachment.readStatus).isEqualTo(AttachmentReadStatus.TOO_LARGE)
        assertThat(attachment.localPath).isEmpty()
    }
}

private object BitmapFactoryCompat {
    fun decode(file: File): Boolean =
        android.graphics.BitmapFactory.decodeFile(file.absolutePath) != null
}
