package com.notifplus.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.notifplus.domain.model.AutoDismissRule
import com.notifplus.domain.model.ListenerState
import com.notifplus.domain.model.NotificationListenerHealth
import com.notifplus.domain.model.RetentionSettings
import com.notifplus.domain.repository.AutoDismissRepository
import com.notifplus.domain.repository.NotificationListenerHealthRepository
import com.notifplus.domain.repository.RetentionRepository
import com.notifplus.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notifPlusDataStore by preferencesDataStore(name = "notifplus_preferences")

private object PreferenceKeys {
    val archiveRetentionEnabled = booleanPreferencesKey("archive_retention_enabled")
    val archiveRetentionDays = intPreferencesKey("archive_retention_days")
    val autoDismissPackages = stringSetPreferencesKey("auto_dismiss_packages")
    val listenerState = stringPreferencesKey("listener_state")
    val listenerLastConnectedAt = longPreferencesKey("listener_last_connected_at")
    val listenerLastPostedAt = longPreferencesKey("listener_last_posted_at")
    val listenerLastPersistedAt = longPreferencesKey("listener_last_persisted_at")
    val listenerQueueDepth = intPreferencesKey("listener_queue_depth")
    val listenerConsecutiveFailures = intPreferencesKey("listener_consecutive_failures")
}

class AutoDismissRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoDismissRepository {
    override fun observeRules(): Flow<List<AutoDismissRule>> = context.notifPlusDataStore.data.map { preferences ->
        preferences[PreferenceKeys.autoDismissPackages].orEmpty()
            .sorted()
            .map { AutoDismissRule(packageName = it, enabled = true) }
    }

    override suspend fun isEnabledFor(packageName: String): Boolean =
        context.notifPlusDataStore.data.first()[PreferenceKeys.autoDismissPackages].orEmpty().contains(packageName)

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        context.notifPlusDataStore.edit { preferences ->
            val packages = preferences[PreferenceKeys.autoDismissPackages].orEmpty().toMutableSet()
            if (enabled) packages += packageName else packages -= packageName
            preferences[PreferenceKeys.autoDismissPackages] = packages
        }
    }
}

class RetentionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : RetentionRepository {
    override fun observeSettings(): Flow<RetentionSettings> = context.notifPlusDataStore.data.map { preferences ->
        RetentionSettings(
            enabled = preferences[PreferenceKeys.archiveRetentionEnabled] ?: true,
            days = preferences[PreferenceKeys.archiveRetentionDays] ?: RetentionSettings.DEFAULT_RETENTION_DAYS,
        )
    }

    override suspend fun getSettings(): RetentionSettings = observeSettings().first()

    override suspend fun setSettings(settings: RetentionSettings) {
        context.notifPlusDataStore.edit { preferences ->
            preferences[PreferenceKeys.archiveRetentionEnabled] = settings.enabled
            preferences[PreferenceKeys.archiveRetentionDays] = settings.days
        }
    }
}

@Singleton
class NotificationListenerHealthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : NotificationListenerHealthRepository {
    private val health = MutableStateFlow(NotificationListenerHealth())
    private val persistLock = Any()
    private var persistJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val preferences = context.notifPlusDataStore.data.first()
            health.update { current ->
                current.copy(
                    // A process restart invalidates the previous live connection.
                    // The service must report onListenerConnected() again before UI
                    // presents the listener as live.
                    state = ListenerState.DISCONNECTED,
                    lastConnectedAt = current.lastConnectedAt
                        ?: preferences[PreferenceKeys.listenerLastConnectedAt],
                    lastPostedAt = current.lastPostedAt
                        ?: preferences[PreferenceKeys.listenerLastPostedAt],
                    lastPersistedAt = current.lastPersistedAt
                        ?: preferences[PreferenceKeys.listenerLastPersistedAt],
                    queueDepth = 0,
                    consecutiveFailures = preferences[PreferenceKeys.listenerConsecutiveFailures] ?: 0,
                )
            }
        }
    }

    override fun observeHealth(): Flow<NotificationListenerHealth> = health.asStateFlow()

    override fun current(): NotificationListenerHealth = health.value

    override suspend fun markConnected(at: Long) = update(
        transform = { it.copy(state = ListenerState.CONNECTED, lastConnectedAt = at, queueDepth = 0, consecutiveFailures = 0) },
    )

    override suspend fun markDisconnected(at: Long) = update(
        transform = { it.copy(state = ListenerState.DISCONNECTED, queueDepth = 0) },
    )

    override suspend fun markReconnecting(at: Long) = update(
        transform = { it.copy(state = ListenerState.RECONNECTING) },
    )

    override suspend fun markAccessRequired(at: Long) = update(
        transform = { it.copy(state = ListenerState.ACCESS_REQUIRED, queueDepth = 0) },
    )

    override suspend fun markPosted(at: Long) = update(
        transform = { it.copy(lastPostedAt = at) },
    )

    override suspend fun markPersisted(at: Long) = update(
        transform = { it.copy(lastPersistedAt = at, consecutiveFailures = 0) },
    )

    override suspend fun markFailure() = update(
        transform = { it.copy(consecutiveFailures = it.consecutiveFailures + 1) },
    )

    override fun setQueueDepth(depth: Int) {
        health.update { it.copy(queueDepth = depth.coerceAtLeast(0)) }
    }

    private suspend fun update(
        transform: (NotificationListenerHealth) -> NotificationListenerHealth,
    ) {
        health.update(transform)
        schedulePersist()
    }

    private fun schedulePersist() {
        synchronized(persistLock) {
            if (persistJob?.isActive == true) return
            persistJob = scope.launch(Dispatchers.IO) {
                delay(HEALTH_PERSIST_DEBOUNCE_MS)
                val snapshot = health.value
                runCatching {
                    context.notifPlusDataStore.edit { preferences ->
                        preferences[PreferenceKeys.listenerState] = snapshot.state.name
                        snapshot.lastConnectedAt?.let { preferences[PreferenceKeys.listenerLastConnectedAt] = it }
                        snapshot.lastPostedAt?.let { preferences[PreferenceKeys.listenerLastPostedAt] = it }
                        snapshot.lastPersistedAt?.let { preferences[PreferenceKeys.listenerLastPersistedAt] = it }
                        preferences[PreferenceKeys.listenerQueueDepth] = snapshot.queueDepth
                        preferences[PreferenceKeys.listenerConsecutiveFailures] = snapshot.consecutiveFailures
                    }
                }
                synchronized(persistLock) { persistJob = null }
            }
        }
    }

    private companion object {
        const val HEALTH_PERSIST_DEBOUNCE_MS = 500L
    }
}
