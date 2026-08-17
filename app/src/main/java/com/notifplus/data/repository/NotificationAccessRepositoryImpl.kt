package com.notifplus.data.repository

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
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

        // Step 1: Standard API requestRebind
        NotificationListenerService.requestRebind(componentName)

        // Step 2: Toggle component state via PackageManager to resurrect zombie binder connections
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        true
    }.getOrDefault(false)

    override fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService<PowerManager>() ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    override fun requestIgnoreBatteryOptimizations() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }
}
