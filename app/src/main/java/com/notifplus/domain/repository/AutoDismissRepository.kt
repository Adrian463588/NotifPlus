package com.notifplus.domain.repository

import com.notifplus.domain.model.AutoDismissRule
import kotlinx.coroutines.flow.Flow

interface AutoDismissRepository {
    fun observeRules(): Flow<List<AutoDismissRule>>
    suspend fun isEnabledFor(packageName: String): Boolean
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}
