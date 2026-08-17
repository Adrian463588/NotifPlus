package com.notifplus.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.notifplus.R
import com.notifplus.domain.model.NotificationListenerHealth
import com.notifplus.presentation.HistoryViewModel
import com.notifplus.ui.components.AccessBanner
import com.notifplus.ui.components.EmptyState
import com.notifplus.ui.components.NotificationCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    accessGranted: Boolean,
    listenerHealth: NotificationListenerHealth,
    onRequestAccess: () -> Unit,
    onRequestRebind: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    isIgnoringBatteryOptimizations: Boolean = true,
    onRequestBatteryOptimization: () -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notifications = viewModel.notifications.collectAsLazyPagingItems()
    val knownPackages by viewModel.knownPackages.collectAsStateWithLifecycle(initialValue = emptyList())
    var filterExpanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.history)) })

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            item {
                AccessBanner(
                    accessGranted = accessGranted,
                    listenerHealth = listenerHealth,
                    onRequestAccess = onRequestAccess,
                    onRequestRebind = onRequestRebind,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestBatteryOptimization = onRequestBatteryOptimization,
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.searchText,
                    onValueChange = viewModel::onSearchChanged,
                    label = { Text(stringResource(R.string.search_notifications)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = uiState.searchText.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(onClick = { viewModel.onSearchChanged("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.close))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (knownPackages.isNotEmpty()) {
                item {
                    ExposedDropdownMenuBox(
                        expanded = filterExpanded,
                        onExpandedChange = { filterExpanded = !filterExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = uiState.packageName ?: stringResource(R.string.all_apps),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.filter_by_app)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = filterExpanded,
                            onDismissRequest = { filterExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.all_apps)) },
                                onClick = {
                                    viewModel.setPackageFilter(null)
                                    filterExpanded = false
                                },
                            )
                            knownPackages.forEach { packageName ->
                                DropdownMenuItem(
                                    text = { Text(packageName) },
                                    onClick = {
                                        viewModel.setPackageFilter(packageName)
                                        filterExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (notifications.loadState.refresh is LoadState.Loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            if (notifications.itemCount == 0 && notifications.loadState.refresh is LoadState.NotLoading) {
                item {
                    EmptyState(
                        title = stringResource(R.string.no_notifications),
                        message = stringResource(
                            if (uiState.searchText.isNotEmpty() || uiState.packageName != null) {
                                R.string.no_notifications_filtered
                            } else {
                                R.string.no_notifications
                            },
                        ),
                    )
                }
            } else {
                items(
                    count = notifications.itemCount,
                    key = { index -> notifications.peek(index)?.threadId ?: index.toString() },
                ) { index ->
                    notifications[index]?.let { record ->
                        NotificationCard(
                            summary = record,
                            onClick = { onOpenDetail(record.threadId) },
                            onToggleRead = { viewModel.markRead(record.threadId, !record.isRead) },
                            onToggleFavorite = { viewModel.setFavorite(record.threadId, !record.isFavorite) },
                            onDelete = { viewModel.delete(record.threadId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (notifications.loadState.append is LoadState.Loading) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}
