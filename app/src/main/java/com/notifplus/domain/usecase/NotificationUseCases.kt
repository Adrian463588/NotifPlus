package com.notifplus.domain.usecase

import com.notifplus.domain.model.AutoDismissStatus
import com.notifplus.domain.model.NotificationCapture
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.RemovalOrigin
import com.notifplus.domain.model.RemovalReason
import com.notifplus.domain.repository.AutoDismissRepository
import com.notifplus.domain.repository.NotificationRepository
import com.notifplus.domain.repository.RetentionRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

data class CaptureResult(
    val snapshot: NotificationSnapshot,
    val shouldAutoDismiss: Boolean,
)

class CaptureNotificationUseCase @Inject constructor(
    private val repository: NotificationRepository,
    private val autoDismissRepository: AutoDismissRepository,
) {
    suspend operator fun invoke(
        capture: NotificationCapture,
        allowAutoDismiss: Boolean = true,
    ): CaptureResult {
        // appendCapture is the commit boundary. Auto-dismiss is never reached before it succeeds.
        repository.appendCapture(capture)
        val snapshot = capture.snapshot
        val autoDismissEnabled = autoDismissRepository.isEnabledFor(snapshot.packageName)
        val shouldAutoDismiss = allowAutoDismiss &&
            autoDismissEnabled &&
            snapshot.isClearable &&
            !snapshot.isOngoing

        if (allowAutoDismiss && autoDismissEnabled && !shouldAutoDismiss) {
            repository.updateAutoDismissStatus(snapshot.threadId, AutoDismissStatus.SKIPPED_NOT_CLEARABLE)
        }
        return CaptureResult(snapshot, shouldAutoDismiss)
    }
}

interface NotificationDismissalPort {
    fun cancel(notificationKey: String)
}

class AutoDismissOriginalNotificationUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke(
        snapshot: NotificationSnapshot,
        port: NotificationDismissalPort,
    ): Boolean {
        if (!snapshot.isClearable || snapshot.isOngoing) {
            repository.updateAutoDismissStatus(snapshot.threadId, AutoDismissStatus.SKIPPED_NOT_CLEARABLE)
            return false
        }

        return try {
            repository.updateAutoDismissStatus(snapshot.threadId, AutoDismissStatus.REQUESTED)
            port.cancel(snapshot.notificationKey)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            runCatching {
                repository.updateAutoDismissStatus(snapshot.threadId, AutoDismissStatus.FAILED)
            }
            false
        }
    }
}

class MarkNotificationRemovedUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke(
        threadId: String,
        removedAt: Long,
        reason: RemovalReason,
        reasonCode: Int,
        origin: RemovalOrigin,
    ) {
        repository.markRemoved(
            threadId = threadId,
            removedAt = removedAt,
            reason = reason,
            reasonCode = reasonCode,
            origin = origin,
            dismissedByNotifPlus = origin == RemovalOrigin.NOTIFPLUS,
        )
    }
}

class DeleteExpiredNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository,
    private val retentionRepository: RetentionRepository,
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()) {
        val settings = retentionRepository.getSettings()
        if (!settings.enabled || settings.days < 0) return
        val before = now - settings.days.days.inWholeMilliseconds
        repository.deleteExpired(before)
    }
}

class SetRetentionSettingsUseCase @Inject constructor(
    private val repository: RetentionRepository,
) {
    suspend operator fun invoke(enabled: Boolean, days: Int) {
        require(days in -1..3650) { "Retention must be between manual and 3650 days" }
        repository.setSettings(com.notifplus.domain.model.RetentionSettings(enabled, days))
    }
}
