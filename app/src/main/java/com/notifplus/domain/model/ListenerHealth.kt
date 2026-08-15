package com.notifplus.domain.model

data class NotificationListenerHealth(
    val state: ListenerState = ListenerState.DISCONNECTED,
    val lastConnectedAt: Long? = null,
    val lastPostedAt: Long? = null,
    val lastPersistedAt: Long? = null,
    val queueDepth: Int = 0,
    val consecutiveFailures: Int = 0,
)

enum class ListenerState {
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    ACCESS_REQUIRED,
}
