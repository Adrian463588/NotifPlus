package com.notifplus.domain.usecase

import android.content.Context
import android.net.Uri
import com.notifplus.domain.model.NotificationArchive
import com.notifplus.domain.repository.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import javax.inject.Inject

class NotificationArchiveTransfer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NotificationRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val content = json.encodeToString<NotificationArchive>(repository.exportArchive())
            require(content.toByteArray(Charsets.UTF_8).size <= MAX_TRANSFER_BYTES) { "Export is too large" }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(content)
            } ?: error("Cannot open export destination")
        }
    }

    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readLimitedBytes(MAX_TRANSFER_BYTES + 1).decodeToString()
            } ?: error("Cannot open import source")
            require(content.toByteArray(Charsets.UTF_8).size <= MAX_TRANSFER_BYTES) { "Import is too large" }
            repository.importArchive(json.decodeFromString<NotificationArchive>(content))
        }
    }

    private companion object {
        const val MAX_TRANSFER_BYTES = 20 * 1024 * 1024
    }
}

private fun java.io.InputStream.readLimitedBytes(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        require(output.size() <= limit) { "Transfer is too large" }
    }
    return output.toByteArray()
}
