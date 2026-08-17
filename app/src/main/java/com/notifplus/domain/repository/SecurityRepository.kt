package com.notifplus.domain.repository

import kotlinx.coroutines.flow.Flow

interface SecurityRepository {
    fun observeBiometricLockEnabled(): Flow<Boolean>
    suspend fun isBiometricLockEnabled(): Boolean
    suspend fun setBiometricLockEnabled(enabled: Boolean)
}
