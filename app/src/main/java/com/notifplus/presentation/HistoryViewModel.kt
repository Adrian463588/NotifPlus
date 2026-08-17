package com.notifplus.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.notifplus.domain.model.HistoryQuery
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val searchText: String = "",
    val packageName: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HistoryViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {
    private val _searchText = MutableStateFlow("")
    private val _packageFilter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(_searchText, _packageFilter) { text, pkg ->
        HistoryUiState(searchText = text, packageName = pkg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    val notifications: Flow<PagingData<NotificationThreadSummary>> = combine(
        _searchText.debounce(300L),
        _packageFilter,
    ) { text, pkg ->
        HistoryQuery(text.trim(), pkg)
    }.flatMapLatest { query ->
        repository.observeHistory(query)
    }.cachedIn(viewModelScope)

    val knownPackages = repository.observeKnownPackages()

    fun onSearchChanged(value: String) {
        _searchText.value = value
    }

    fun setPackageFilter(packageName: String?) {
        _packageFilter.value = packageName
    }

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
