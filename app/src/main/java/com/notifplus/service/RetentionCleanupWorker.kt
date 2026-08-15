package com.notifplus.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notifplus.domain.usecase.DeleteExpiredNotificationsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RetentionCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deleteExpiredNotifications: DeleteExpiredNotificationsUseCase,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = runCatching {
        deleteExpiredNotifications()
        Result.success()
    }.getOrElse { Result.retry() }
}
