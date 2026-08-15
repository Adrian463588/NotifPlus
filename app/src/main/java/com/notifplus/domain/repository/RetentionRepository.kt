package com.notifplus.domain.repository

import com.notifplus.domain.model.RetentionSettings
import kotlinx.coroutines.flow.Flow

interface RetentionRepository {
    fun observeSettings(): Flow<RetentionSettings>
    suspend fun getSettings(): RetentionSettings
    suspend fun setSettings(settings: RetentionSettings)
}
