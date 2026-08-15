package com.notifplus.service

import android.app.Notification
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.notifplus.domain.model.AttachmentKind
import com.notifplus.domain.model.AttachmentSourceType
import com.notifplus.domain.model.CaptureOrigin
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class NotificationPayloadExtractorInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val extractor = NotificationPayloadExtractor(context)

    @Test
    fun extractsMessagingImageUri() {
        val source = File(context.cacheDir, "message-${System.nanoTime()}.png")
        FileOutputStream(source).use { output ->
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
        }
        val sender = Person.Builder().setName("Sender").build()
        val style = Notification.MessagingStyle(sender)
            .addMessage(
                Notification.MessagingStyle.Message("photo", 1L, sender)
                    .setData("image/png", Uri.fromFile(source)),
            )
        val notification = builder().setStyle(style).build()
        val capture = extractor.extract(sbn(notification), null, CaptureOrigin.LIVE_POST)

        assertThat(capture.attachmentCandidates.map { it.kind }).contains(AttachmentKind.MESSAGE_MEDIA)
        val candidate = capture.attachmentCandidates.first { it.kind == AttachmentKind.MESSAGE_MEDIA }
        assertThat(candidate.sourceType).isEqualTo(AttachmentSourceType.CONTENT_URI)
        assertThat(candidate.sourceUri).isEqualTo(Uri.fromFile(source).toString())
        source.delete()
    }

    @Test
    fun extractsBigPictureBitmap() {
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        val notification = builder().build().apply {
            extras.putParcelable(Notification.EXTRA_PICTURE, bitmap)
        }
        val capture = extractor.extract(sbn(notification), null, CaptureOrigin.LIVE_POST)

        val candidate = capture.attachmentCandidates.first { it.kind == AttachmentKind.BIG_PICTURE }
        assertThat(candidate.sourceType).isEqualTo(AttachmentSourceType.INLINE_BITMAP)
        assertThat(candidate.inlineBytes).isNotEmpty()
        bitmap.recycle()
    }

    @Test
    fun extractsNativeMessagingExtrasWhenCompatStyleIsUnavailable() {
        val source = File(context.cacheDir, "native-message-${System.nanoTime()}.png")
        FileOutputStream(source).use { output ->
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
        }
        val notification = builder().build().apply {
            val messageBundle = android.os.Bundle().apply {
                putCharSequence("text", "photo")
                putLong("time", 2L)
                putCharSequence("sender", "Messenger sender")
                putString("type", "image/png")
                putParcelable("uri", Uri.fromFile(source))
            }
            extras.putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf<Parcelable>(messageBundle))
        }

        val capture = extractor.extract(sbn(notification), null, CaptureOrigin.LIVE_POST)

        val candidate = capture.attachmentCandidates.first { it.kind == AttachmentKind.MESSAGE_MEDIA }
        assertThat(candidate.sourceUri).isEqualTo(Uri.fromFile(source).toString())
        assertThat(capture.messages).hasSize(1)
        source.delete()
    }

    private fun builder(): Notification.Builder =
        Notification.Builder(context, "notifplus-test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Test notification")

    @Suppress("DEPRECATION")
    private fun sbn(notification: Notification) = StatusBarNotification(
        "com.example.source",
        "com.example.source",
        42,
        "tag",
        1000,
        0,
        0,
        notification,
        Process.myUserHandle(),
        System.currentTimeMillis(),
    )
}
