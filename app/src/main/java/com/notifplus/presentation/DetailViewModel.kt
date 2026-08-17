package com.notifplus.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notifplus.domain.model.NotificationThreadDetail
import com.notifplus.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NotificationRepository,
) : ViewModel() {
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])
    private val _detail = MutableStateFlow<NotificationThreadDetail?>(null)
    val detail: StateFlow<NotificationThreadDetail?> = _detail.asStateFlow()

    init {
        viewModelScope.launch { _detail.value = repository.getThreadDetail(threadId) }
    }

    fun toggleRead() {
        viewModelScope.launch {
            _detail.value?.let { detail ->
                val value = !detail.summary.isRead
                repository.markRead(detail.summary.threadId, value)
                _detail.value = detail.copy(summary = detail.summary.copy(isRead = value))
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            _detail.value?.let { detail ->
                val value = !detail.summary.isFavorite
                repository.markFavorite(detail.summary.threadId, value)
                _detail.value = detail.copy(summary = detail.summary.copy(isFavorite = value))
            }
        }
    }

    fun delete(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _detail.value?.let { detail ->
                repository.delete(detail.summary.threadId)
                _detail.value = null
            }
            onComplete()
        }
    }
}
