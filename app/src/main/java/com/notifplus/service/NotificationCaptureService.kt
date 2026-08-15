package com.notifplus.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationCaptureService : NotificationListenerService() {
    @Inject lateinit var coordinator: NotificationCaptureCoordinator

    override fun onListenerConnected() {
        coordinator.onListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        coordinator.onListenerDisconnected(this)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
    ) {
        coordinator.onNotificationPosted(this, sbn, rankingMap)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        coordinator.onNotificationRemoved(this, sbn, reason)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        coordinator.onRankingUpdate(this, rankingMap)
    }

    override fun onDestroy() {
        coordinator.onServiceDestroyed(this)
        super.onDestroy()
    }
}
