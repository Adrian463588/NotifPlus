package com.notifplus.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_threads (
                threadId TEXT NOT NULL PRIMARY KEY,
                notificationKey TEXT NOT NULL,
                userProfileKey TEXT NOT NULL,
                packageName TEXT NOT NULL,
                appLabel TEXT NOT NULL,
                latestSnapshotId TEXT,
                latestCapturedAt INTEGER NOT NULL,
                latestPostedAt INTEGER NOT NULL,
                revisionCount INTEGER NOT NULL,
                isActive INTEGER NOT NULL,
                removedAt INTEGER,
                removalReason TEXT NOT NULL,
                removalReasonCode INTEGER,
                removalOrigin TEXT NOT NULL,
                isRead INTEGER NOT NULL,
                isFavorite INTEGER NOT NULL,
                autoDismissStatus TEXT NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_snapshots (
                snapshotId TEXT NOT NULL PRIMARY KEY,
                threadId TEXT NOT NULL,
                notificationKey TEXT NOT NULL,
                userProfileKey TEXT NOT NULL,
                packageName TEXT NOT NULL,
                appLabel TEXT NOT NULL,
                title TEXT NOT NULL,
                titleBig TEXT NOT NULL,
                text TEXT NOT NULL,
                bigText TEXT NOT NULL,
                subText TEXT NOT NULL,
                infoText TEXT NOT NULL,
                summaryText TEXT NOT NULL,
                conversationTitle TEXT NOT NULL,
                tickerText TEXT NOT NULL,
                template TEXT NOT NULL,
                textLinesJson TEXT NOT NULL,
                remoteInputHistoryJson TEXT NOT NULL,
                structuredExtrasJson TEXT NOT NULL,
                payloadAvailability TEXT NOT NULL,
                previewText TEXT NOT NULL,
                category TEXT NOT NULL,
                channelId TEXT NOT NULL,
                channelName TEXT NOT NULL,
                postedAt INTEGER NOT NULL,
                capturedAt INTEGER NOT NULL,
                isOngoing INTEGER NOT NULL,
                isClearable INTEGER NOT NULL,
                isGroupSummary INTEGER NOT NULL,
                isConversation INTEGER NOT NULL,
                importance INTEGER,
                rank INTEGER,
                captureOrigin TEXT NOT NULL,
                FOREIGN KEY(threadId) REFERENCES notification_threads(threadId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_messages (
                messageId TEXT NOT NULL PRIMARY KEY,
                snapshotId TEXT NOT NULL,
                isHistoric INTEGER NOT NULL,
                ordinal INTEGER NOT NULL,
                sender TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                mimeType TEXT NOT NULL,
                dataUri TEXT NOT NULL,
                FOREIGN KEY(snapshotId) REFERENCES notification_snapshots(snapshotId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_attachments (
                attachmentId TEXT NOT NULL PRIMARY KEY,
                snapshotId TEXT NOT NULL,
                sourceUri TEXT NOT NULL,
                localPath TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                readStatus TEXT NOT NULL,
                FOREIGN KEY(snapshotId) REFERENCES notification_snapshots(snapshotId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_threads_packageName ON notification_threads(packageName)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_threads_notificationKey ON notification_threads(notificationKey)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_threads_latestCapturedAt ON notification_threads(latestCapturedAt)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_snapshots_threadId_capturedAt ON notification_snapshots(threadId, capturedAt)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_snapshots_notificationKey ON notification_snapshots(notificationKey)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_snapshots_packageName ON notification_snapshots(packageName)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_messages_snapshotId ON notification_messages(snapshotId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_attachments_snapshotId ON notification_attachments(snapshotId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_attachments_sourceUri ON notification_attachments(sourceUri)")

        database.execSQL(
            """
            INSERT OR IGNORE INTO notification_threads(
                threadId, notificationKey, userProfileKey, packageName, appLabel,
                latestSnapshotId, latestCapturedAt, latestPostedAt, revisionCount,
                isActive, removedAt, removalReason, removalReasonCode, removalOrigin,
                isRead, isFavorite, autoDismissStatus
            )
            SELECT
                'legacy:' || recordId,
                notificationKey,
                CAST(userProfileId AS TEXT),
                packageName,
                appLabel,
                'legacy:' || recordId,
                updatedAt,
                postedAt,
                1,
                isActive,
                removedAt,
                removalReason,
                NULL,
                CASE
                    WHEN removalReason = 'LISTENER_CANCEL' THEN 'NOTIFPLUS'
                    WHEN removalReason IN ('APP_CANCEL', 'APP_CANCEL_ALL') THEN 'SOURCE_APP'
                    WHEN removalReason = 'USER_DISMISSED' THEN 'USER'
                    ELSE 'UNKNOWN'
                END,
                isRead,
                isFavorite,
                CASE
                    WHEN autoDismissStatus = 'REQUESTED' AND removalReason = 'LISTENER_CANCEL'
                        THEN 'REMOVED_BY_NOTIFPLUS'
                    ELSE autoDismissStatus
                END
            FROM notifications
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT OR IGNORE INTO notification_snapshots(
                snapshotId, threadId, notificationKey, userProfileKey, packageName, appLabel,
                title, titleBig, text, bigText, subText, infoText, summaryText,
                conversationTitle, tickerText, template, textLinesJson, remoteInputHistoryJson,
                structuredExtrasJson, payloadAvailability, previewText, category, channelId,
                channelName, postedAt, capturedAt, isOngoing, isClearable, isGroupSummary,
                isConversation, importance, rank, captureOrigin
            )
            SELECT
                'legacy:' || recordId,
                'legacy:' || recordId,
                notificationKey,
                CAST(userProfileId AS TEXT),
                packageName,
                appLabel,
                title,
                '',
                body,
                body,
                subText,
                '',
                '',
                '',
                '',
                '',
                '[]',
                '[]',
                '{}',
                CASE WHEN contentUnavailable = 1 THEN 'NO_TEXT_IN_DELIVERED_PAYLOAD' ELSE 'AVAILABLE' END,
                body,
                category,
                channelId,
                channelName,
                postedAt,
                updatedAt,
                isOngoing,
                isClearable,
                isGroupSummary,
                isConversation,
                importance,
                rank,
                'LEGACY'
            FROM notifications
            """.trimIndent(),
        )
        // The legacy rows have been copied above; remove the obsolete mutable table so
        // future writes can only use the append-only schema.
        database.execSQL("DROP TABLE IF EXISTS notifications")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE notification_attachments ADD COLUMN kind TEXT NOT NULL DEFAULT 'UNKNOWN'",
        )
        database.execSQL(
            "ALTER TABLE notification_attachments ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'CONTENT_URI'",
        )
        database.execSQL(
            "ALTER TABLE notification_attachments ADD COLUMN contentDescription TEXT NOT NULL DEFAULT ''",
        )
        database.execSQL(
            "ALTER TABLE notification_attachments ADD COLUMN pixelWidth INTEGER",
        )
        database.execSQL(
            "ALTER TABLE notification_attachments ADD COLUMN pixelHeight INTEGER",
        )
    }
}
