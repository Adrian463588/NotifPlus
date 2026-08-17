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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    modifier: Modifier = Modifier,
    isIgnoringBatteryOptimizations: Boolean = true,
    onRequestBatteryOptimization: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val retention by viewModel.retentionSettings.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricLockEnabled.collectAsStateWithLifecycle()
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::import)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings)) })

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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

            operationError?.let { error ->
                item {
                    Text(
                        text = stringResource(R.string.operation_failed, error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Text(stringResource(R.string.archive_retention), style = MaterialTheme.typography.titleLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.retention_enabled), modifier = Modifier.weight(1f))
                    Switch(
                        checked = retention.enabled,
                        onCheckedChange = { viewModel.setRetention(it, retention.days) },
                    )
                }
                RetentionOptions(retention, viewModel::setRetention)

                OutlinedButton(
                    onClick = viewModel::deleteExpiredNow,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_now))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    OutlinedButton(onClick = { exportLauncher.launch("notifplus-export.json") }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_archive))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.import_archive))
                    }
                }
            }

            item {
                HorizontalDivider()
                Text(stringResource(R.string.original_auto_dismiss), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.auto_dismiss_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = onOpenApps,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.manage_app_rules))
                }
            }

            operationMessage?.let { msg ->
                item {
                    Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp),
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = viewModel::clearMessages) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.close),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.biometric_lock_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.biometric_lock_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { viewModel.setBiometricLockEnabled(it) },
                    )
                }
            }

            item {
                HorizontalDivider()
                Text(stringResource(R.string.security_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.security_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(
                selected = settings.days == days,
                onClick = { onChange(settings.enabled, days) },
            )
            Text(
                text = if (days == RetentionSettings.MANUAL_ONLY) {
                    stringResource(R.string.retention_manual)
                } else {
                    stringResource(R.string.retention_days_format, days)
                },
            )
        }
    }
}
