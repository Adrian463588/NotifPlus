package com.notifplus.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.content.getSystemService
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.service.NotificationCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationAccessRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationAccessRepository {
    private val componentName = ComponentName(context, NotificationCaptureService::class.java)
    private val _accessGranted = MutableStateFlow(isAccessGranted())

    override fun observeAccessGranted(): Flow<Boolean> = _accessGranted.asStateFlow()

    override fun isAccessGranted(): Boolean = context.getSystemService<android.app.NotificationManager>()
        ?.isNotificationListenerAccessGranted(componentName)
        ?: false

    override fun refreshAccessState() {
        _accessGranted.value = isAccessGranted()
    }

    override fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
    }

    override fun requestRebind(): Boolean = runCatching {
        if (!isAccessGranted()) return false
        NotificationListenerService.requestRebind(componentName)
        true
    }.getOrDefault(false)
}
