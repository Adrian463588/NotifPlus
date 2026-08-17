package com.notifplus.domain.repository

import kotlinx.coroutines.flow.Flow

interface NotificationAccessRepository {
    fun observeAccessGranted(): Flow<Boolean>
    fun isAccessGranted(): Boolean
    fun refreshAccessState()
    fun openSystemSettings()
    fun requestRebind(): Boolean
    fun isIgnoringBatteryOptimizations(): Boolean
    fun requestIgnoreBatteryOptimizations()
}
