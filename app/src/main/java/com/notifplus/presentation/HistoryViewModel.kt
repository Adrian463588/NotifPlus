package com.notifplus.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.notifplus.domain.model.HistoryQuery
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class HistoryUiState(
    val searchText: String = "",
    val packageName: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val notifications: Flow<PagingData<NotificationThreadSummary>> = _uiState
        .flatMapLatest { state -> repository.observeHistory(HistoryQuery(state.searchText, state.packageName)) }
        .cachedIn(viewModelScope)

    val knownPackages = repository.observeKnownPackages()

    fun onSearchChanged(value: String) = _uiState.update { it.copy(searchText = value) }

    fun setPackageFilter(packageName: String?) = _uiState.update { it.copy(packageName = packageName) }

    fun markRead(threadId: String, isRead: Boolean) {
        viewModelScope.launch { repository.markRead(threadId, isRead) }
    }

    fun setFavorite(threadId: String, isFavorite: Boolean) {
        viewModelScope.launch { repository.markFavorite(threadId, isFavorite) }
    }

    fun delete(threadId: String) {
        viewModelScope.launch { repository.delete(threadId) }
    }
}
