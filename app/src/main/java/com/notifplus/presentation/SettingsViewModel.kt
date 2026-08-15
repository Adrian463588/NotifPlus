package com.notifplus.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.notifplus.domain.model.AutoDismissRule
import com.notifplus.domain.model.RetentionSettings
import com.notifplus.domain.repository.AutoDismissRepository
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.domain.repository.NotificationRepository
import com.notifplus.domain.repository.RetentionRepository
import com.notifplus.domain.usecase.DeleteExpiredNotificationsUseCase
import com.notifplus.domain.usecase.SetRetentionSettingsUseCase
import com.notifplus.domain.usecase.NotificationArchiveTransfer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRuleUiModel(
    val packageName: String,
    val enabled: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accessRepository: NotificationAccessRepository,
    private val retentionRepository: RetentionRepository,
    private val autoDismissRepository: AutoDismissRepository,
    private val notificationRepository: NotificationRepository,
    private val setRetentionSettings: SetRetentionSettingsUseCase,
    private val deleteExpiredNotifications: DeleteExpiredNotificationsUseCase,
    private val archiveTransfer: NotificationArchiveTransfer,
) : ViewModel() {
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    val accessGranted: StateFlow<Boolean> = accessRepository.observeAccessGranted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), accessRepository.isAccessGranted())

    val retentionSettings: StateFlow<RetentionSettings> = retentionRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RetentionSettings())

    val appRules: Flow<List<AppRuleUiModel>> = combine(
        notificationRepository.observeKnownPackages(),
        autoDismissRepository.observeRules(),
    ) { packages, rules ->
        val enabledPackages = rules.filter(AutoDismissRule::enabled).mapTo(hashSetOf(), AutoDismissRule::packageName)
        packages.map { AppRuleUiModel(it, it in enabledPackages) }
    }

    fun refreshAccess() = accessRepository.refreshAccessState()

    fun openAccessSettings() = accessRepository.openSystemSettings()

    fun setRetention(enabled: Boolean, days: Int) {
        runSafely { setRetentionSettings(enabled, days) }
    }

    fun deleteExpiredNow() {
        runSafely { deleteExpiredNotifications() }
    }

    fun setAutoDismiss(packageName: String, enabled: Boolean) {
        runSafely { autoDismissRepository.setEnabled(packageName, enabled) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _operationError.value = null
            archiveTransfer.export(uri).exceptionOrNull()?.let(::reportFailure)
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _operationError.value = null
            archiveTransfer.import(uri).exceptionOrNull()?.let(::reportFailure)
        }
    }

    private fun runSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            _operationError.value = null
            runCatching { block() }.onFailure(::reportFailure)
        }
    }

    private fun reportFailure(error: Throwable) {
        _operationError.value = error.message ?: "Operasi tidak dapat diselesaikan"
    }
}
