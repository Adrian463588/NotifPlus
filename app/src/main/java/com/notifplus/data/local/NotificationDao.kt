package com.notifplus.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query(
        """
        SELECT
            t.threadId,
            t.notificationKey,
            t.userProfileKey,
            t.packageName,
            t.appLabel,
            s.snapshotId AS latestSnapshotId,
            s.capturedAt AS latestCapturedAt,
            s.postedAt AS latestPostedAt,
            s.title AS latestTitle,
            s.previewText AS latestPreviewText,
            s.payloadAvailability AS latestPayloadAvailability,
            t.revisionCount,
            t.isActive,
            t.removedAt,
            t.removalReason,
            t.removalReasonCode,
            t.removalOrigin,
            t.isRead,
            t.isFavorite,
            t.autoDismissStatus
        FROM notification_threads t
        INNER JOIN notification_snapshots s ON s.snapshotId = t.latestSnapshotId
        WHERE (:searchText = '' OR EXISTS (
            SELECT 1 FROM notification_snapshots searched
            WHERE searched.threadId = t.threadId
              AND (
                searched.title LIKE '%' || :searchText || '%'
                OR searched.titleBig LIKE '%' || :searchText || '%'
                OR searched.text LIKE '%' || :searchText || '%'
                OR searched.bigText LIKE '%' || :searchText || '%'
                OR searched.subText LIKE '%' || :searchText || '%'
                OR searched.infoText LIKE '%' || :searchText || '%'
                OR searched.summaryText LIKE '%' || :searchText || '%'
                OR searched.conversationTitle LIKE '%' || :searchText || '%'
                OR searched.previewText LIKE '%' || :searchText || '%'
                OR searched.textLinesJson LIKE '%' || :searchText || '%'
                OR searched.remoteInputHistoryJson LIKE '%' || :searchText || '%'
                OR searched.structuredExtrasJson LIKE '%' || :searchText || '%'
              )
        ) OR EXISTS (
            SELECT 1 FROM notification_messages message
            INNER JOIN notification_snapshots messageSnapshot
                ON messageSnapshot.snapshotId = message.snapshotId
            WHERE messageSnapshot.threadId = t.threadId
              AND (
                message.sender LIKE '%' || :searchText || '%'
                OR message.text LIKE '%' || :searchText || '%'
                OR message.dataUri LIKE '%' || :searchText || '%'
              )
        ) OR EXISTS (
            SELECT 1 FROM notification_attachments attachment
            INNER JOIN notification_snapshots attachmentSnapshot
                ON attachmentSnapshot.snapshotId = attachment.snapshotId
            WHERE attachmentSnapshot.threadId = t.threadId
              AND (
                attachment.sourceUri LIKE '%' || :searchText || '%'
                OR attachment.mimeType LIKE '%' || :searchText || '%'
                OR attachment.readStatus LIKE '%' || :searchText || '%'
              )
        ) OR t.appLabel LIKE '%' || :searchText || '%' OR t.packageName LIKE '%' || :searchText || '%')
          AND (:packageName IS NULL OR t.packageName = :packageName)
          AND (:onlyUnread = 0 OR t.isRead = 0)
          AND (:onlyFavorites = 0 OR t.isFavorite = 1)
          AND (:onlyWithMedia = 0 OR EXISTS (
              SELECT 1 FROM notification_attachments att
              INNER JOIN notification_snapshots attSnap ON attSnap.snapshotId = att.snapshotId
              WHERE attSnap.threadId = t.threadId
          ))
        ORDER BY s.capturedAt DESC
        """,
    )
    fun pagingSource(
        searchText: String,
        packageName: String?,
        onlyUnread: Boolean = false,
        onlyFavorites: Boolean = false,
        onlyWithMedia: Boolean = false,
    ): PagingSource<Int, NotificationThreadSummaryRow>

    @Query("SELECT * FROM notification_threads WHERE threadId = :threadId LIMIT 1")
    suspend fun findThread(threadId: String): NotificationThreadEntity?

    @Query("SELECT * FROM notification_threads WHERE notificationKey = :notificationKey ORDER BY latestCapturedAt DESC LIMIT 1")
    suspend fun findLatestThreadByNotificationKey(notificationKey: String): NotificationThreadEntity?

    @Query("SELECT * FROM notification_snapshots WHERE snapshotId = :snapshotId LIMIT 1")
    suspend fun findSnapshot(snapshotId: String): NotificationSnapshotEntity?

    @Query("SELECT * FROM notification_snapshots WHERE threadId = :threadId ORDER BY capturedAt DESC")
    suspend fun snapshotsForThread(threadId: String): List<NotificationSnapshotEntity>

    @Query("SELECT * FROM notification_snapshots WHERE threadId = :threadId ORDER BY capturedAt ASC")
    suspend fun snapshotsForThreadAscending(threadId: String): List<NotificationSnapshotEntity>

    @Query("SELECT * FROM notification_messages WHERE snapshotId = :snapshotId ORDER BY isHistoric, ordinal")
    suspend fun messagesForSnapshot(snapshotId: String): List<NotificationMessageEntity>

    @Query("SELECT * FROM notification_attachments WHERE snapshotId = :snapshotId ORDER BY attachmentId")
    suspend fun attachmentsForSnapshot(snapshotId: String): List<NotificationAttachmentEntity>

    @Query("SELECT DISTINCT packageName FROM notification_threads ORDER BY packageName")
    fun observeKnownPackages(): Flow<List<String>>

    @Query("SELECT * FROM notification_threads ORDER BY latestCapturedAt DESC")
    suspend fun allThreads(): List<NotificationThreadEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertThread(entity: NotificationThreadEntity)

    @Update
    suspend fun updateThread(entity: NotificationThreadEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshot(entity: NotificationSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessages(entities: List<NotificationMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachments(entities: List<NotificationAttachmentEntity>)

    @Transaction
    suspend fun appendCapture(
        proposedThread: NotificationThreadEntity,
        snapshot: NotificationSnapshotEntity,
        messages: List<NotificationMessageEntity>,
        attachments: List<NotificationAttachmentEntity>,
    ) {
        val existing = findThread(proposedThread.threadId)
        val thread = proposedThread.copy(
            latestSnapshotId = snapshot.snapshotId,
            revisionCount = (existing?.revisionCount ?: 0) + 1,
            isRead = existing?.isRead ?: proposedThread.isRead,
            isFavorite = existing?.isFavorite ?: proposedThread.isFavorite,
            autoDismissStatus = existing?.autoDismissStatus ?: proposedThread.autoDismissStatus,
        )
        if (existing == null) insertThread(thread) else updateThread(thread)
        insertSnapshot(snapshot)
        if (messages.isNotEmpty()) insertMessages(messages)
        if (attachments.isNotEmpty()) insertAttachments(attachments)
    }

    @Query(
        """
        UPDATE notification_threads
        SET removedAt = :removedAt,
            removalReason = :reason,
            removalReasonCode = :reasonCode,
            removalOrigin = :origin,
            isActive = 0,
            autoDismissStatus = CASE
                WHEN :dismissedByNotifPlus = 1 THEN 'REMOVED_BY_NOTIFPLUS'
                ELSE autoDismissStatus
            END
        WHERE threadId = :threadId
        """,
    )
    suspend fun markThreadRemoved(
        threadId: String,
        removedAt: Long,
        reason: String,
        reasonCode: Int,
        origin: String,
        dismissedByNotifPlus: Boolean,
    )

    @Query(
        """
        UPDATE notification_threads
        SET latestSnapshotId = :latestSnapshotId,
            latestCapturedAt = :latestCapturedAt,
            latestPostedAt = :latestPostedAt,
            revisionCount = :revisionCount,
            isActive = :isActive,
            removedAt = :removedAt,
            removalReason = :removalReason,
            removalReasonCode = :removalReasonCode,
            removalOrigin = :removalOrigin,
            isRead = :isRead,
            isFavorite = :isFavorite,
            autoDismissStatus = :autoDismissStatus
        WHERE threadId = :threadId
        """,
    )
    suspend fun updateThreadState(
        threadId: String,
        latestSnapshotId: String,
        latestCapturedAt: Long,
        latestPostedAt: Long,
        revisionCount: Int,
        isActive: Boolean,
        removedAt: Long?,
        removalReason: String,
        removalReasonCode: Int?,
        removalOrigin: String,
        isRead: Boolean,
        isFavorite: Boolean,
        autoDismissStatus: String,
    )

    @Query("UPDATE notification_threads SET autoDismissStatus = :status WHERE threadId = :threadId")
    suspend fun updateAutoDismissStatus(threadId: String, status: String)

    @Query("UPDATE notification_snapshots SET importance = :importance, rank = :rank WHERE snapshotId = :snapshotId")
    suspend fun updateRanking(snapshotId: String, importance: Int?, rank: Int?)

    @Query("UPDATE notification_threads SET isRead = :isRead WHERE threadId = :threadId")
    suspend fun markRead(threadId: String, isRead: Boolean)

    @Query("UPDATE notification_threads SET isFavorite = :isFavorite WHERE threadId = :threadId")
    suspend fun markFavorite(threadId: String, isFavorite: Boolean)

    @Query("DELETE FROM notification_threads WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    @Query(
        """
        SELECT localPath FROM notification_attachments
        INNER JOIN notification_snapshots ON notification_snapshots.snapshotId = notification_attachments.snapshotId
        WHERE notification_snapshots.capturedAt < :before AND localPath != ''
        """,
    )
    suspend fun expiredAttachmentPaths(before: Long): List<String>

    @Query(
        """
        SELECT localPath FROM notification_attachments
        INNER JOIN notification_snapshots ON notification_snapshots.snapshotId = notification_attachments.snapshotId
        WHERE notification_snapshots.threadId = :threadId AND localPath != ''
        """,
    )
    suspend fun attachmentPathsForThread(threadId: String): List<String>

    @Transaction
    suspend fun deleteExpired(before: Long) {
        deleteExpiredSnapshots(before)
        deleteEmptyThreads()
    }

    @Query("DELETE FROM notification_snapshots WHERE capturedAt < :before")
    suspend fun deleteExpiredSnapshots(before: Long)

    @Query("DELETE FROM notification_threads WHERE NOT EXISTS (SELECT 1 FROM notification_snapshots WHERE threadId = notification_threads.threadId)")
    suspend fun deleteEmptyThreads()

    @Query("DELETE FROM notification_threads")
    suspend fun deleteAll()
}
