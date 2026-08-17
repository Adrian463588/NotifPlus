package com.notifplus.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.notifplus.di.ApplicationScope
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.domain.usecase.DeleteExpiredNotificationsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accessRepository: NotificationAccessRepository

    @Inject
    lateinit var deleteExpiredNotifications: DeleteExpiredNotificationsUseCase

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(LOG_TAG, "BootCompletedReceiver triggered with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            accessRepository.refreshAccessState()
            if (accessRepository.isAccessGranted()) {
                Log.i(LOG_TAG, "Notification listener access granted on boot/update; requesting rebind")
                accessRepository.requestRebind()
            }

            appScope.launch {
                runCatching {
                    deleteExpiredNotifications()
                }.onFailure {
                    Log.e(LOG_TAG, "Failed to run expiration cleanup on boot", it)
                }
            }
        }
    }

    private companion object {
        const val LOG_TAG = "NotifPlusBoot"
    }
}
