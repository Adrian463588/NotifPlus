package com.notifplus.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.notifplus.data.local.NotificationAttachmentEntity
import com.notifplus.data.local.NotificationDao
import com.notifplus.data.local.NotificationMessageEntity
import com.notifplus.data.local.NotificationThreadEntity
import com.notifplus.data.local.NotificationThreadSummaryRow
import com.notifplus.data.local.toDomain
import com.notifplus.data.local.toEntity
import com.notifplus.domain.model.AutoDismissStatus
import com.notifplus.domain.model.AttachmentReadStatus
import com.notifplus.domain.model.HistoryQuery
import com.notifplus.domain.model.NotificationArchive
import com.notifplus.domain.model.NotificationCapture
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.NotificationSnapshotWithRelations
import com.notifplus.domain.model.NotificationThreadDetail
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.model.PayloadAvailability
import com.notifplus.domain.model.RemovalOrigin
import com.notifplus.domain.model.RemovalReason
import com.notifplus.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val dao: NotificationDao,
    private val attachmentFileStore: NotificationAttachmentFileStore,
) : NotificationRepository {
    override fun observeHistory(query: HistoryQuery): Flow<PagingData<NotificationThreadSummary>> =
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            dao.pagingSource(query.searchText.trim(), query.packageName)
        }.flow.map { page -> page.map(NotificationThreadSummaryRow::toDomain) }

    override fun observeKnownPackages(): Flow<List<String>> = dao.observeKnownPackages()

    override suspend fun findSnapshot(snapshotId: String): NotificationSnapshot? =
        dao.findSnapshot(snapshotId)?.toDomain()

    override suspend fun findLatestSnapshotByNotificationKey(notificationKey: String): NotificationSnapshot? {
        val thread = dao.findLatestThreadByNotificationKey(notificationKey) ?: return null
        val snapshotId = thread.latestSnapshotId ?: return null
        return dao.findSnapshot(snapshotId)?.toDomain()
    }

    override suspend fun appendCapture(capture: NotificationCapture) {
        val storedAttachments = attachmentFileStore.persist(capture.attachmentCandidates)
        val snapshot = capture.snapshot
        val thread = NotificationThreadEntity(
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
            removalReason = RemovalReason.UNKNOWN.name,
            removalReasonCode = null,
            removalOrigin = RemovalOrigin.UNKNOWN.name,
            isRead = false,
            isFavorite = false,
            autoDismissStatus = AutoDismissStatus.NOT_REQUESTED.name,
        )
        try {
            dao.appendCapture(
                proposedThread = thread,
                snapshot = snapshot.toEntity(),
                messages = capture.messages.map { it.toEntity() },
                attachments = storedAttachments.map { it.toEntity() },
            )
        } catch (error: Throwable) {
            attachmentFileStore.delete(storedAttachments.map { it.localPath })
            throw error
        }
    }

    override suspend fun getThreadDetail(threadId: String): NotificationThreadDetail? {
        val thread = dao.findThread(threadId) ?: return null
        val latestSnapshotId = thread.latestSnapshotId ?: return null
        val latestSnapshot = dao.findSnapshot(latestSnapshotId) ?: return null
        val summary = NotificationThreadSummary(
            threadId = thread.threadId,
            notificationKey = thread.notificationKey,
            userProfileKey = thread.userProfileKey,
            packageName = thread.packageName,
            appLabel = thread.appLabel,
            latestSnapshotId = latestSnapshot.snapshotId,
            latestCapturedAt = latestSnapshot.capturedAt,
            latestPostedAt = latestSnapshot.postedAt,
            latestTitle = latestSnapshot.title,
            latestPreviewText = latestSnapshot.previewText,
            latestPayloadAvailability = payloadAvailabilityOrDefault(latestSnapshot.payloadAvailability),
            revisionCount = thread.revisionCount,
            isActive = thread.isActive,
            removedAt = thread.removedAt,
            removalReason = enumValueOrDefault(thread.removalReason, RemovalReason.UNKNOWN),
            removalReasonCode = thread.removalReasonCode,
            removalOrigin = enumValueOrDefault(thread.removalOrigin, RemovalOrigin.UNKNOWN),
            isRead = thread.isRead,
            isFavorite = thread.isFavorite,
            autoDismissStatus = enumValueOrDefault(thread.autoDismissStatus, AutoDismissStatus.NOT_REQUESTED),
        )
        val snapshots = dao.snapshotsForThreadAscending(threadId).map { snapshot ->
            NotificationSnapshotWithRelations(
                snapshot = snapshot.toDomain(),
                messages = dao.messagesForSnapshot(snapshot.snapshotId).map { it.toDomain() },
                attachments = dao.attachmentsForSnapshot(snapshot.snapshotId).map { it.toDomain() },
            )
        }
        return NotificationThreadDetail(summary, snapshots)
    }

    override suspend fun exportArchive(): NotificationArchive =
        NotificationArchive(
            threads = dao.allThreads().mapNotNull { thread -> getThreadDetail(thread.threadId) },
        )

    override suspend fun importArchive(archive: NotificationArchive): Int {
        var importedSnapshots = 0
        archive.threads.forEach { detail ->
            val importedThreadId = "import:${UUID.randomUUID()}:${detail.summary.threadId}"
            var latestImportedSnapshotId: String? = null
            detail.snapshots.forEach { relation ->
                val old = relation.snapshot
                val importedSnapshotId = "import:${UUID.randomUUID()}:${old.snapshotId}"
                if (old.snapshotId == detail.summary.latestSnapshotId) {
                    latestImportedSnapshotId = importedSnapshotId
                }
                val snapshot = old.copy(
                    snapshotId = importedSnapshotId,
                    threadId = importedThreadId,
                )
                val importedMessages = relation.messages.map { message ->
                    message.copy(
                        messageId = "import:${UUID.randomUUID()}:${message.messageId}",
                        snapshotId = importedSnapshotId,
                    )
                }
                val importedAttachments = relation.attachments.map { attachment ->
                    attachment.copy(
                        attachmentId = "import:${UUID.randomUUID()}:${attachment.attachmentId}",
                        snapshotId = importedSnapshotId,
                        localPath = "",
                        readStatus = AttachmentReadStatus.IMPORTED_METADATA,
                    )
                }
                appendImported(snapshot, importedMessages.map { it.toEntity() }, importedAttachments.map { it.toEntity() })
                importedSnapshots++
            }
            dao.updateThreadState(
                threadId = importedThreadId,
                latestSnapshotId = requireNotNull(latestImportedSnapshotId),
                latestCapturedAt = detail.summary.latestCapturedAt,
                latestPostedAt = detail.summary.latestPostedAt,
                revisionCount = detail.summary.revisionCount,
                isActive = detail.summary.isActive,
                removedAt = detail.summary.removedAt,
                removalReason = detail.summary.removalReason.name,
                removalReasonCode = detail.summary.removalReasonCode,
                removalOrigin = detail.summary.removalOrigin.name,
                isRead = detail.summary.isRead,
                isFavorite = detail.summary.isFavorite,
                autoDismissStatus = detail.summary.autoDismissStatus.name,
            )
        }
        return importedSnapshots
    }

    private suspend fun appendImported(
        snapshot: NotificationSnapshot,
        messages: List<NotificationMessageEntity>,
        attachments: List<NotificationAttachmentEntity>,
    ) {
        val thread = NotificationThreadEntity(
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
            removalReason = RemovalReason.UNKNOWN.name,
            removalReasonCode = null,
            removalOrigin = RemovalOrigin.UNKNOWN.name,
            isRead = false,
            isFavorite = false,
            autoDismissStatus = AutoDismissStatus.NOT_REQUESTED.name,
        )
        dao.appendCapture(thread, snapshot.toEntity(), messages, attachments)
    }

    override suspend fun markRemoved(
        threadId: String,
        removedAt: Long,
        reason: RemovalReason,
        reasonCode: Int,
        origin: RemovalOrigin,
        dismissedByNotifPlus: Boolean,
    ) = dao.markThreadRemoved(threadId, removedAt, reason.name, reasonCode, origin.name, dismissedByNotifPlus)

    override suspend fun updateRanking(snapshotId: String, importance: Int?, rank: Int?) =
        dao.updateRanking(snapshotId, importance, rank)

    override suspend fun updateAutoDismissStatus(threadId: String, status: AutoDismissStatus) =
        dao.updateAutoDismissStatus(threadId, status.name)

    override suspend fun markRead(threadId: String, isRead: Boolean) = dao.markRead(threadId, isRead)

    override suspend fun markFavorite(threadId: String, isFavorite: Boolean) = dao.markFavorite(threadId, isFavorite)

    override suspend fun delete(threadId: String) {
        val paths = dao.attachmentPathsForThread(threadId)
        dao.deleteThread(threadId)
        attachmentFileStore.delete(paths)
    }

    override suspend fun deleteExpired(before: Long) {
        val paths = dao.expiredAttachmentPaths(before)
        dao.deleteExpired(before)
        attachmentFileStore.delete(paths)
    }

    override suspend fun deleteAll() {
        val paths = dao.allThreads().flatMap { dao.attachmentPathsForThread(it.threadId) }
        dao.deleteAll()
        attachmentFileStore.delete(paths)
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)

private fun payloadAvailabilityOrDefault(value: String): PayloadAvailability =
    runCatching { PayloadAvailability.valueOf(value) }
        .getOrDefault(PayloadAvailability.NO_TEXT_IN_DELIVERED_PAYLOAD)
