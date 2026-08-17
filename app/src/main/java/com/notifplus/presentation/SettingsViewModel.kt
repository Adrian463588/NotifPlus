package com.notifplus.presentation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notifplus.domain.model.AutoDismissRule
import com.notifplus.domain.model.RetentionSettings
import com.notifplus.domain.repository.AutoDismissRepository
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.domain.repository.NotificationRepository
import com.notifplus.domain.repository.RetentionRepository
import com.notifplus.domain.usecase.DeleteExpiredNotificationsUseCase
import com.notifplus.domain.usecase.NotificationArchiveTransfer
import com.notifplus.domain.usecase.SetRetentionSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRuleUiModel(
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    val accessGranted: StateFlow<Boolean> = accessRepository.observeAccessGranted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), accessRepository.isAccessGranted())

    val retentionSettings: StateFlow<RetentionSettings> = retentionRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RetentionSettings())

    val appRules: Flow<List<AppRuleUiModel>> = combine(
        notificationRepository.observeKnownPackages(),
        autoDismissRepository.observeRules(),
        _appSearchQuery,
    ) { capturedPackages, rules, query ->
        val pm = context.packageManager
        val installedApps = runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(mainIntent, 0).mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())

        val allUniquePackages = (capturedPackages + installedApps + rules.map { it.packageName }).distinct()
        val enabledPackages = rules.filter(AutoDismissRule::enabled).mapTo(hashSetOf(), AutoDismissRule::packageName)

        val models = allUniquePackages.map { pkg ->
            val label = runCatching {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(pkg)

            AppRuleUiModel(
                packageName = pkg,
                appLabel = label,
                enabled = pkg in enabledPackages,
            )
        }

        if (query.isBlank()) {
            models.sortedWith(compareByDescending<AppRuleUiModel> { it.enabled }.thenBy { it.appLabel.lowercase() })
        } else {
            val cleanQuery = query.trim().lowercase()
            models.filter {
                it.appLabel.lowercase().contains(cleanQuery) || it.packageName.lowercase().contains(cleanQuery)
            }.sortedWith(compareByDescending<AppRuleUiModel> { it.enabled }.thenBy { it.appLabel.lowercase() })
        }
    }.flowOn(Dispatchers.IO)

    fun onAppSearchQueryChanged(query: String) {
        _appSearchQuery.value = query
    }

    fun refreshAccess() = accessRepository.refreshAccessState()

    fun openAccessSettings() = accessRepository.openSystemSettings()

    fun requestIgnoreBatteryOptimizations() = accessRepository.requestIgnoreBatteryOptimizations()

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
