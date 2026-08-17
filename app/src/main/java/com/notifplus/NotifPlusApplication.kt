package com.notifplus

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.notifplus.service.RetentionCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.domain.usecase.DeleteExpiredNotificationsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class NotifPlusApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var deleteExpiredNotifications: DeleteExpiredNotificationsUseCase
    @Inject lateinit var accessRepository: NotificationAccessRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { deleteExpiredNotifications() }
            if (accessRepository.isAccessGranted()) {
                accessRepository.requestRebind()
            }
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notifplus-retention-cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionCleanupWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
