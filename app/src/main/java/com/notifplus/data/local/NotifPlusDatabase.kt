package com.notifplus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NotificationThreadEntity::class,
        NotificationSnapshotEntity::class,
        NotificationMessageEntity::class,
        NotificationAttachmentEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class NotifPlusDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}
