package com.notifplus.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notifplus.domain.model.ListenerState
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.domain.repository.NotificationListenerHealthRepository
import com.notifplus.domain.usecase.DeleteExpiredNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(repository.isIgnoringBatteryOptimizations())
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    fun refresh() {
        repository.refreshAccessState()
        _isIgnoringBatteryOptimizations.value = repository.isIgnoringBatteryOptimizations()
        viewModelScope.launch {
            if (!repository.isAccessGranted()) {
                healthRepository.markAccessRequired()
            } else if (healthRepository.current().state != ListenerState.CONNECTED) {
                requestRebind()
            }
        }
    }

    fun openSettings() = repository.openSystemSettings()

    fun requestIgnoreBatteryOptimizations() {
        repository.requestIgnoreBatteryOptimizations()
    }

    fun requestRebind() {
        viewModelScope.launch {
            if (!repository.isAccessGranted()) {
                healthRepository.markAccessRequired()
                return@launch
            }
            healthRepository.markReconnecting()
            repository.requestRebind()

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
        val REBIND_DELAYS_MS = longArrayOf(0L, 3_000L, 10_000L, 30_000L)
    }
}
