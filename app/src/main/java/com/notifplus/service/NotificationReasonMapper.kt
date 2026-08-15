package com.notifplus.service

import android.service.notification.NotificationListenerService
import com.notifplus.domain.model.RemovalReason
import com.notifplus.domain.model.RemovalOrigin

fun Int.toRemovalReason(): RemovalReason = when (this) {
    NotificationListenerService.REASON_CLICK -> RemovalReason.CLICK
    NotificationListenerService.REASON_CANCEL -> RemovalReason.CANCEL
    NotificationListenerService.REASON_CANCEL_ALL -> RemovalReason.CANCEL_ALL
    NotificationListenerService.REASON_ERROR -> RemovalReason.ERROR
    NotificationListenerService.REASON_PACKAGE_CHANGED -> RemovalReason.PACKAGE_CHANGED
    NotificationListenerService.REASON_LISTENER_CANCEL -> RemovalReason.LISTENER_CANCEL
    NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED -> RemovalReason.GROUP_SUMMARY_CANCELED
    NotificationListenerService.REASON_CLEAR_DATA -> RemovalReason.CLEAR_DATA
    NotificationListenerService.REASON_ASSISTANT_CANCEL -> RemovalReason.ASSISTANT_CANCEL
    NotificationListenerService.REASON_LOCKDOWN -> RemovalReason.LOCKDOWN
    NotificationListenerService.REASON_USER_STOPPED -> RemovalReason.USER_STOPPED
    NotificationListenerService.REASON_PACKAGE_BANNED -> RemovalReason.PACKAGE_BANNED
    NotificationListenerService.REASON_APP_CANCEL -> RemovalReason.APP_CANCEL
    NotificationListenerService.REASON_APP_CANCEL_ALL -> RemovalReason.APP_CANCEL_ALL
    NotificationListenerService.REASON_GROUP_OPTIMIZATION -> RemovalReason.GROUP_OPTIMIZATION
    NotificationListenerService.REASON_PACKAGE_SUSPENDED -> RemovalReason.PACKAGE_SUSPENDED
    NotificationListenerService.REASON_PROFILE_TURNED_OFF -> RemovalReason.PROFILE_TURNED_OFF
    NotificationListenerService.REASON_UNAUTOBUNDLED -> RemovalReason.UNAUTOBUNDLED
    NotificationListenerService.REASON_CHANNEL_BANNED -> RemovalReason.CHANNEL_BANNED
    NotificationListenerService.REASON_SNOOZED -> RemovalReason.SNOOZED
    NotificationListenerService.REASON_TIMEOUT -> RemovalReason.TIMEOUT
    NotificationListenerService.REASON_CHANNEL_REMOVED -> RemovalReason.CHANNEL_REMOVED
    else -> RemovalReason.UNKNOWN
}

fun Int.toRemovalOrigin(): RemovalOrigin = when (this) {
    NotificationListenerService.REASON_LISTENER_CANCEL,
    NotificationListenerService.REASON_LISTENER_CANCEL_ALL,
    -> RemovalOrigin.NOTIFPLUS

    NotificationListenerService.REASON_APP_CANCEL,
    NotificationListenerService.REASON_APP_CANCEL_ALL,
    NotificationListenerService.REASON_PACKAGE_CHANGED,
    NotificationListenerService.REASON_PACKAGE_BANNED,
    NotificationListenerService.REASON_PACKAGE_SUSPENDED,
    NotificationListenerService.REASON_CHANNEL_BANNED,
    NotificationListenerService.REASON_CLEAR_DATA,
    -> RemovalOrigin.SOURCE_APP

    NotificationListenerService.REASON_CLICK,
    NotificationListenerService.REASON_CANCEL,
    NotificationListenerService.REASON_CANCEL_ALL,
    -> RemovalOrigin.USER

    NotificationListenerService.REASON_ERROR,
    NotificationListenerService.REASON_TIMEOUT,
    NotificationListenerService.REASON_LOCKDOWN,
    NotificationListenerService.REASON_PROFILE_TURNED_OFF,
    -> RemovalOrigin.SYSTEM

    else -> RemovalOrigin.UNKNOWN
}
