package com.notifplus.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notifplus.di.ApplicationScope
import com.notifplus.domain.model.AutoDismissStatus
import com.notifplus.domain.model.CaptureOrigin
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.domain.repository.NotificationListenerHealthRepository
import com.notifplus.domain.repository.NotificationRepository
import com.notifplus.domain.usecase.AutoDismissOriginalNotificationUseCase
import com.notifplus.domain.usecase.CaptureNotificationUseCase
import com.notifplus.domain.usecase.DeleteExpiredNotificationsUseCase
import com.notifplus.domain.usecase.MarkNotificationRemovedUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationCaptureCoordinator @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val extractor: NotificationPayloadExtractor,
    private val captureNotification: CaptureNotificationUseCase,
    private val autoDismissOriginal: AutoDismissOriginalNotificationUseCase,
    private val deleteExpiredNotifications: DeleteExpiredNotificationsUseCase,
    private val markNotificationRemoved: MarkNotificationRemovedUseCase,
    private val repository: NotificationRepository,
    private val accessRepository: NotificationAccessRepository,
    private val healthRepository: NotificationListenerHealthRepository,
) {
    private sealed interface ListenerEvent {
        data object Bootstrap : ListenerEvent

        data class Posted(
            val sbn: StatusBarNotification,
            val rankingMap: NotificationListenerService.RankingMap?,
            val allowAutoDismiss: Boolean,
            val captureOrigin: CaptureOrigin,
            val enqueuedAt: Long = System.currentTimeMillis(),
            val retryAttempt: Int = 0,
        ) : ListenerEvent

        data class Removed(
            val sbn: StatusBarNotification,
            val reason: Int,
            val retryAttempt: Int = 0,
        ) : ListenerEvent

    }

    private data class ListenerSession(
        val service: NotificationListenerService,
        val token: Long,
        val workerScope: CoroutineScope,
        val queue: NotificationEventQueue<ListenerEvent>,
        val rankingQueue: Channel<NotificationListenerService.RankingMap>,
        val accepting: AtomicBoolean = AtomicBoolean(true),
        val draining: AtomicBoolean = AtomicBoolean(false),
    )

    private val lifecycleLock = Any()
    private val callbackExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(LOG_TAG, "Notification listener worker failed", throwable)
        scope.launch { healthRepository.markFailure() }
    }
    private var nextToken = 0L
    private var session: ListenerSession? = null
    private var rebindJob: Job? = null
    private var lastRetentionCleanupAt = 0L

    fun onListenerConnected(service: NotificationListenerService) {
        val connectedSession = synchronized(lifecycleLock) {
            rebindJob?.cancel()
            rebindJob = null
            session?.let { previous ->
                previous.accepting.set(false)
                previous.draining.set(true)
                previous.queue.close()
                previous.rankingQueue.close()
            }
            val token = ++nextToken
            val job = SupervisorJob(scope.coroutineContext[Job])
            val workerScope = CoroutineScope(
                scope.coroutineContext + job + callbackExceptionHandler + Dispatchers.Default,
            )
            ListenerSession(
                service = service,
                token = token,
                workerScope = workerScope,
                queue = NotificationEventQueue(),
                rankingQueue = Channel(Channel.CONFLATED),
            ).also { session = it }
        }

        Log.i(LOG_TAG, "Notification listener connected")
        scope.launch { healthRepository.markConnected() }
        connectedSession.workerScope.launch {
            consumeEvents(connectedSession)
        }
        connectedSession.workerScope.launch {
            consumeRankings(connectedSession)
        }
        enqueue(connectedSession, ListenerEvent.Bootstrap)
    }

    fun onListenerDisconnected(service: NotificationListenerService) {
        val disconnected = synchronized(lifecycleLock) {
            if (session?.service !== service) {
                false
            } else {
                session?.accepting?.set(false)
                session?.draining?.set(true)
                session?.queue?.close()
                session?.rankingQueue?.close()
                session = null
                nextToken++
                true
            }
        }
        if (!disconnected) return

        Log.w(LOG_TAG, "Notification listener disconnected; scheduling rebind")
        scope.launch { healthRepository.markDisconnected() }
        scheduleRebind(service)
    }

    fun onServiceDestroyed(service: NotificationListenerService) = onListenerDisconnected(service)

    fun onNotificationPosted(
        service: NotificationListenerService,
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap?,
    ) {
        currentSession(service)?.let { current ->
            enqueue(
                current,
                ListenerEvent.Posted(
                    sbn = sbn,
                    rankingMap = rankingMap,
                    allowAutoDismiss = true,
                    captureOrigin = CaptureOrigin.LIVE_POST,
                ),
            )
        }
    }

    fun onNotificationRemoved(
        service: NotificationListenerService,
        sbn: StatusBarNotification,
        reason: Int,
    ) {
        currentSession(service)?.let { current ->
            enqueue(current, ListenerEvent.Removed(sbn = sbn, reason = reason))
        }
    }

    fun onRankingUpdate(
        service: NotificationListenerService,
        rankingMap: NotificationListenerService.RankingMap,
    ) {
        currentSession(service)?.let { current ->
            if (current.accepting.get()) current.rankingQueue.trySend(rankingMap)
        }
    }

    private suspend fun consumeEvents(listenerSession: ListenerSession) {
        while (currentCoroutineContext().isActive) {
            val event = listenerSession.queue.receive() ?: break
            healthRepository.setQueueDepth(listenerSession.queue.depth)
            try {
                when (event) {
                    ListenerEvent.Bootstrap -> processBootstrap(listenerSession)
                    is ListenerEvent.Posted -> processPosted(listenerSession, event)
                    is ListenerEvent.Removed -> processRemoved(listenerSession, event)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                healthRepository.markFailure()
                Log.e(LOG_TAG, "Notification listener event failed", throwable)
            }
        }
    }

    private suspend fun consumeRankings(listenerSession: ListenerSession) {
        while (currentCoroutineContext().isActive) {
            val rankingMap = listenerSession.rankingQueue.receiveCatching().getOrNull() ?: break
            processRanking(listenerSession, rankingMap)
        }
    }

    private suspend fun processBootstrap(listenerSession: ListenerSession) {
        val activeNotifications = try {
            listenerSession.service.activeNotifications.toList()
        } catch (failure: Throwable) {
            healthRepository.markFailure()
            Log.e(LOG_TAG, "Unable to read active notifications", failure)
            emptyList()
        }

        activeNotifications.forEach { sbn ->
            processPosted(
                listenerSession,
                ListenerEvent.Posted(
                    sbn = sbn,
                    rankingMap = null,
                    allowAutoDismiss = false,
                    captureOrigin = CaptureOrigin.RECONNECTED,
                ),
            )
        }
    }

    private suspend fun processPosted(
        listenerSession: ListenerSession,
        event: ListenerEvent.Posted,
    ) {
        if (!canProcess(listenerSession)) return

        val input = try {
            extractor.extract(event.sbn, event.rankingMap, event.captureOrigin)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            healthRepository.markFailure()
            Log.e(LOG_TAG, "Unable to extract notification payload", failure)
            return
        }

        healthRepository.markPosted(event.enqueuedAt)
        val result = try {
            captureNotification(input, event.allowAutoDismiss)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            healthRepository.markFailure()
            Log.e(LOG_TAG, "Unable to persist notification; retry scheduled", failure)
            schedulePostedRetry(listenerSession, event)
            return
        }

        healthRepository.markPersisted()
        scheduleRetentionCleanupIfDue()
        if (!result.shouldAutoDismiss || !isCurrent(listenerSession)) return

        val currentNotification = currentNotification(listenerSession, result.snapshot.notificationKey)
        if (currentNotification == null || !currentNotification.isClearable || currentNotification.isOngoing) {
            if (isCurrent(listenerSession) && currentNotification != null) {
                repository.updateAutoDismissStatus(
                    result.snapshot.threadId,
                    AutoDismissStatus.SKIPPED_NOT_CLEARABLE,
                )
            }
            return
        }

        autoDismissOriginal(
            result.snapshot,
            object : com.notifplus.domain.usecase.NotificationDismissalPort {
                override fun cancel(notificationKey: String) {
                    synchronized(lifecycleLock) {
                        check(isCurrentLocked(listenerSession)) { "listener session is no longer active" }
                        val latest = currentNotificationLocked(listenerSession, notificationKey)
                        check(latest != null && latest.isClearable && !latest.isOngoing) {
                            "notification is no longer dismissible"
                        }
                        listenerSession.service.cancelNotification(notificationKey)
                    }
                }
            },
        )
    }

    private suspend fun processRemoved(
        listenerSession: ListenerSession,
        event: ListenerEvent.Removed,
    ) {
        if (!canProcess(listenerSession)) return
        try {
            markNotificationRemoved(
                threadId = extractor.recordThreadId(event.sbn),
                removedAt = System.currentTimeMillis(),
                reason = event.reason.toRemovalReason(),
                reasonCode = event.reason,
                origin = event.reason.toRemovalOrigin(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            healthRepository.markFailure()
            Log.e(LOG_TAG, "Unable to persist removal event; retry scheduled", failure)
            scheduleRemovedRetry(listenerSession, event)
        }
    }

    private suspend fun processRanking(
        listenerSession: ListenerSession,
        rankingMap: NotificationListenerService.RankingMap,
    ) {
        if (!isCurrent(listenerSession)) return
        rankingMap.orderedKeys.forEach { key ->
            repository.findLatestSnapshotByNotificationKey(key)?.let { snapshot ->
                val ranking = NotificationListenerService.Ranking()
                if (rankingMap.getRanking(key, ranking)) {
                    repository.updateRanking(snapshot.snapshotId, ranking.importance, ranking.rank)
                }
            }
        }
    }

    private fun schedulePostedRetry(
        listenerSession: ListenerSession,
        event: ListenerEvent.Posted,
    ) {
        val retryDelay = RETRY_DELAYS_MS.getOrElse(event.retryAttempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)) {
            RETRY_DELAYS_MS.last()
        }
        listenerSession.workerScope.launch {
            delay(retryDelay)
            if (isCurrent(listenerSession)) {
                enqueue(listenerSession, event.copy(retryAttempt = event.retryAttempt + 1))
            }
        }
    }

    private fun scheduleRemovedRetry(
        listenerSession: ListenerSession,
        event: ListenerEvent.Removed,
    ) {
        val retryDelay = RETRY_DELAYS_MS.getOrElse(event.retryAttempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)) {
            RETRY_DELAYS_MS.last()
        }
        listenerSession.workerScope.launch {
            delay(retryDelay)
            if (isCurrent(listenerSession)) {
                enqueue(listenerSession, event.copy(retryAttempt = event.retryAttempt + 1))
            }
        }
    }

    private fun enqueue(listenerSession: ListenerSession, event: ListenerEvent): Boolean {
        if (!isCurrent(listenerSession) || !listenerSession.accepting.get() || !listenerSession.workerScope.isActive) return false
        val offered = listenerSession.queue.offer(event)
        healthRepository.setQueueDepth(listenerSession.queue.depth)
        if (!offered) Log.w(LOG_TAG, "Notification listener event queue is closed")
        return offered
    }

    private fun scheduleRebind(service: NotificationListenerService) {
        synchronized(lifecycleLock) {
            rebindJob?.cancel()
            rebindJob = scope.launch {
                REBIND_DELAYS_MS.forEachIndexed { index, retryDelay ->
                    if (index > 0) delay(retryDelay)
                    if (isConnectedTo(service)) return@launch
                    if (!accessRepository.isAccessGranted()) {
                        healthRepository.markAccessRequired()
                        return@launch
                    }
                    healthRepository.markReconnecting()
                    if (!accessRepository.requestRebind()) {
                        healthRepository.markFailure()
                    }
                }
                if (!isConnectedTo(service)) healthRepository.markDisconnected()
            }
        }
    }

    private fun scheduleRetentionCleanupIfDue() {
        val now = System.currentTimeMillis()
        val shouldRun = synchronized(lifecycleLock) {
            if (now - lastRetentionCleanupAt < RETENTION_CLEANUP_INTERVAL_MS) {
                false
            } else {
                lastRetentionCleanupAt = now
                true
            }
        }
        if (shouldRun) {
            scope.launch(Dispatchers.IO) {
                runCatching { deleteExpiredNotifications(now) }
                    .onFailure { Log.e(LOG_TAG, "Retention cleanup failed", it) }
            }
        }
    }

    private fun currentSession(service: NotificationListenerService): ListenerSession? =
        synchronized(lifecycleLock) {
            session?.takeIf { it.service === service }
        }

    private fun isConnectedTo(service: NotificationListenerService): Boolean = synchronized(lifecycleLock) {
        session?.service === service
    }

    private fun isCurrent(listenerSession: ListenerSession): Boolean = synchronized(lifecycleLock) {
        isCurrentLocked(listenerSession)
    }

    private fun canProcess(listenerSession: ListenerSession): Boolean =
        isCurrent(listenerSession) || listenerSession.draining.get()

    private fun isCurrentLocked(listenerSession: ListenerSession): Boolean =
        session?.service === listenerSession.service && session?.token == listenerSession.token

    private fun currentNotification(
        listenerSession: ListenerSession,
        notificationKey: String,
    ): StatusBarNotification? = synchronized(lifecycleLock) {
        currentNotificationLocked(listenerSession, notificationKey)
    }

    private fun currentNotificationLocked(
        listenerSession: ListenerSession,
        notificationKey: String,
    ): StatusBarNotification? {
        if (!isCurrentLocked(listenerSession)) return null
        return runCatching {
            listenerSession.service.activeNotifications.firstOrNull { it.key == notificationKey }
        }.getOrNull()
    }

    private companion object {
        const val LOG_TAG = "NotifPlusListener"
        const val RETENTION_CLEANUP_INTERVAL_MS = 15 * 60 * 1_000L
        val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 60_000L)
        val REBIND_DELAYS_MS = longArrayOf(0L, 5_000L, 15_000L, 60_000L)
    }
}
