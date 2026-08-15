package com.notifplus.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifplus.R
import com.notifplus.domain.model.NotificationListenerHealth
import com.notifplus.domain.model.RetentionSettings
import com.notifplus.presentation.SettingsViewModel
import com.notifplus.ui.components.AccessBanner

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    accessGranted: Boolean,
    listenerHealth: NotificationListenerHealth,
    onRequestAccess: () -> Unit,
    onRequestRebind: () -> Unit,
    onOpenApps: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val retention by viewModel.retentionSettings.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::import)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings)) })
        AccessBanner(accessGranted, listenerHealth, onRequestAccess, onRequestRebind, Modifier.padding(horizontal = 16.dp))
        operationError?.let { error ->
            Text(
                text = stringResource(R.string.operation_failed, error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.archive_retention), style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.retention_enabled), modifier = Modifier.weight(1f))
                    Switch(checked = retention.enabled, onCheckedChange = { viewModel.setRetention(it, retention.days) })
                }
                RetentionOptions(retention, viewModel::setRetention)
                OutlinedButton(onClick = viewModel::deleteExpiredNow) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_now))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("notifplus-export.json") }) {
                        Text(stringResource(R.string.export_archive))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Text(stringResource(R.string.import_archive))
                    }
                }
            }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.original_auto_dismiss), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.auto_dismiss_warning), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenApps, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.manage_app_rules))
                }
            }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.security_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.security_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RetentionOptions(
    settings: RetentionSettings,
    onChange: (Boolean, Int) -> Unit,
) {
    RetentionSettings.OPTIONS.forEach { days ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioButton(selected = settings.days == days, onClick = { onChange(settings.enabled, days) })
            Text(
                text = if (days == RetentionSettings.MANUAL_ONLY) stringResource(R.string.retention_manual)
                else stringResource(R.string.retention_days_format, days),
            )
        }
    }
}
