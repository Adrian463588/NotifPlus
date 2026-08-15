package com.notifplus.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notifplus.domain.model.ListenerState
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.domain.repository.NotificationListenerHealthRepository
import com.notifplus.domain.usecase.DeleteExpiredNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccessViewModel @Inject constructor(
    private val repository: NotificationAccessRepository,
    private val healthRepository: NotificationListenerHealthRepository,
    private val deleteExpiredNotifications: DeleteExpiredNotificationsUseCase,
) : ViewModel() {
    val accessGranted: StateFlow<Boolean> = repository.observeAccessGranted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.isAccessGranted())

    val listenerHealth = healthRepository.observeHealth()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), healthRepository.current())

    fun refresh() {
        repository.refreshAccessState()
        viewModelScope.launch {
            if (!repository.isAccessGranted()) {
                healthRepository.markAccessRequired()
            } else if (healthRepository.current().state != ListenerState.CONNECTED) {
                requestRebind()
            }
        }
    }

    fun openSettings() = repository.openSystemSettings()

    fun requestRebind() {
        viewModelScope.launch {
            if (!repository.isAccessGranted()) {
                healthRepository.markAccessRequired()
                return@launch
            }
            REBIND_DELAYS_MS.forEachIndexed { index, retryDelay ->
                if (index > 0) delay(retryDelay)
                if (healthRepository.current().state == ListenerState.CONNECTED) return@launch
                healthRepository.markReconnecting()
                if (!repository.requestRebind()) {
                    healthRepository.markFailure()
                }
            }
            if (healthRepository.current().state != ListenerState.CONNECTED) {
                healthRepository.markDisconnected()
            }
        }
    }

    fun cleanupExpiredArchive() {
        viewModelScope.launch {
            runCatching { deleteExpiredNotifications() }
        }
    }

    private companion object {
        val REBIND_DELAYS_MS = longArrayOf(0L, 5_000L, 15_000L, 60_000L)
    }
}
